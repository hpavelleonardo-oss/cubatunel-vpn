package com.example.vpn

import android.util.Base64
import com.example.data.ProxyProfileEntity
import com.example.model.DiagnosticProbeResult
import com.example.model.ProxyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

object ProxyDialer {

    fun connectThroughProxy(
        profile: ProxyProfileEntity,
        targetHost: String,
        targetPort: Int,
        timeoutMs: Int = 6500,
        protectSocket: (Socket) -> Boolean
    ): Socket {
        val type = profile.resolvedProxyType()
        val socket = Socket()
        protectSocket(socket)
        socket.tcpNoDelay = true
        socket.keepAlive = true
        socket.soTimeout = timeoutMs

        if (type == ProxyType.DOH_DIRECT) {
            socket.connect(InetSocketAddress(targetHost, targetPort), timeoutMs)
            return socket
        }

        socket.connect(InetSocketAddress(profile.host, profile.port), timeoutMs)

        return when (type) {
            ProxyType.SOCKS5 -> {
                performSocks5Handshake(socket, profile, targetHost, targetPort)
                socket
            }
            ProxyType.SOCKS4 -> {
                performSocks4aHandshake(socket, targetHost, targetPort)
                socket
            }
            ProxyType.HTTP_CONNECT, ProxyType.HTTP_PAYLOAD -> {
                performHttpConnectHandshake(socket, profile, targetHost, targetPort)
                socket
            }
            ProxyType.SSL_TLS_SNI, ProxyType.WS_TLS_CDN -> {
                val tlsSocket = wrapWithTlsSni(socket, profile.host, profile.port, profile.sniHost)
                if (profile.httpPayload.isNotBlank()) {
                    performHttpConnectHandshake(tlsSocket, profile, targetHost, targetPort)
                }
                tlsSocket
            }
            else -> {
                // For SSH / Shadowsocks / VLESS / VMess / Trojan / SlowDNS upstream transport sockets:
                // establish protected TCP/TLS carrier socket ready for framing/control.
                if (type == ProxyType.TROJAN || type == ProxyType.VLESS || type == ProxyType.VMESS) {
                    if (profile.sniHost.isNotBlank() || profile.port == 443) {
                        wrapWithTlsSni(socket, profile.host, profile.port, profile.sniHost)
                    } else socket
                } else socket
            }
        }
    }

    private fun performSocks5Handshake(
        socket: Socket,
        profile: ProxyProfileEntity,
        targetHost: String,
        targetPort: Int
    ) {
        val out = socket.getOutputStream()
        val input = socket.getInputStream()

        val hasAuth = profile.username.isNotBlank()
        val greeting = if (hasAuth) {
            byteArrayOf(0x05, 0x02, 0x00, 0x02)
        } else {
            byteArrayOf(0x05, 0x01, 0x00)
        }
        out.write(greeting)
        out.flush()

        val methodReply = ByteArray(2)
        readFully(input, methodReply)
        if (methodReply[0] != 0x05.toByte()) {
            throw IllegalStateException("Respuesta SOCKS5 inválida")
        }

        when (methodReply[1].toInt() and 0xFF) {
            0x00 -> Unit
            0x02 -> {
                val u = profile.username.toByteArray(StandardCharsets.UTF_8)
                val p = profile.password.toByteArray(StandardCharsets.UTF_8)
                val authReq = ByteArray(3 + u.size + p.size)
                authReq[0] = 0x01
                authReq[1] = u.size.toByte()
                System.arraycopy(u, 0, authReq, 2, u.size)
                authReq[2 + u.size] = p.size.toByte()
                System.arraycopy(p, 0, authReq, 3 + u.size, p.size)
                out.write(authReq)
                out.flush()
                val authResp = ByteArray(2)
                readFully(input, authResp)
                if (authResp[1] != 0x00.toByte()) {
                    throw IllegalStateException("Autenticación SOCKS5 rechazada")
                }
            }
            else -> throw IllegalStateException("Método SOCKS5 no soportado: ${methodReply[1]}")
        }

        val addrBytes = runCatching { InetAddress.getByName(targetHost).address }.getOrNull()
        val connectPacket: ByteArray = if (addrBytes != null && addrBytes.size == 4) {
            byteArrayOf(
                0x05, 0x01, 0x00, 0x01,
                addrBytes[0], addrBytes[1], addrBytes[2], addrBytes[3],
                ((targetPort ushr 8) and 0xFF).toByte(),
                (targetPort and 0xFF).toByte()
            )
        } else {
            val hostBytes = targetHost.toByteArray(StandardCharsets.UTF_8)
            ByteArray(7 + hostBytes.size).apply {
                this[0] = 0x05
                this[1] = 0x01
                this[2] = 0x00
                this[3] = 0x03
                this[4] = hostBytes.size.toByte()
                System.arraycopy(hostBytes, 0, this, 5, hostBytes.size)
                this[5 + hostBytes.size] = ((targetPort ushr 8) and 0xFF).toByte()
                this[6 + hostBytes.size] = (targetPort and 0xFF).toByte()
            }
        }
        out.write(connectPacket)
        out.flush()

        val respHead = ByteArray(4)
        readFully(input, respHead)
        if (respHead[1] != 0x00.toByte()) {
            throw IllegalStateException("SOCKS5 CONNECT falló (código ${respHead[1]})")
        }
        val skipLen = when (respHead[3].toInt() and 0xFF) {
            0x01 -> 4 + 2
            0x04 -> 16 + 2
            0x03 -> {
                val len = input.read()
                if (len < 0) 0 else len + 2
            }
            else -> 0
        }
        if (skipLen > 0) {
            readFully(input, ByteArray(skipLen))
        }
    }

    private fun performSocks4aHandshake(
        socket: Socket,
        targetHost: String,
        targetPort: Int
    ) {
        val out = socket.getOutputStream()
        val input = socket.getInputStream()
        val hostBytes = targetHost.toByteArray(StandardCharsets.UTF_8)
        val req = ByteArray(8 + 1 + hostBytes.size + 1)
        req[0] = 0x04
        req[1] = 0x01
        req[2] = ((targetPort ushr 8) and 0xFF).toByte()
        req[3] = (targetPort and 0xFF).toByte()
        req[4] = 0x00; req[5] = 0x00; req[6] = 0x00; req[7] = 0x01
        req[8] = 0x00 // empty user id
        System.arraycopy(hostBytes, 0, req, 9, hostBytes.size)
        req[9 + hostBytes.size] = 0x00
        out.write(req)
        out.flush()

        val resp = ByteArray(8)
        readFully(input, resp)
        if (resp[1] != 0x5A.toByte()) {
            throw IllegalStateException("SOCKS4a rechazado (código ${resp[1]})")
        }
    }

    private fun performHttpConnectHandshake(
        socket: Socket,
        profile: ProxyProfileEntity,
        targetHost: String,
        targetPort: Int
    ) {
        val out = socket.getOutputStream()
        val sni = profile.sniHost.ifBlank { targetHost }
        val hostPort = "$targetHost:$targetPort"
        val renderedPayload = if (profile.httpPayload.isNotBlank()) {
            profile.httpPayload
                .replace("[host_port]", hostPort)
                .replace("[host]", targetHost)
                .replace("[port]", targetPort.toString())
                .replace("[sni]", sni)
                .replace("[crlf]", "\r\n")
                .replace("[cr]", "\r")
                .replace("[lf]", "\n")
        } else {
            "CONNECT $hostPort HTTP/1.1\r\nHost: $hostPort\r\nConnection: Keep-Alive\r\n\r\n"
        }

        val finalRequest = if (profile.username.isNotBlank() &&
            !renderedPayload.contains("Proxy-Authorization", ignoreCase = true)
        ) {
            val token = Base64.encodeToString(
                "${profile.username}:${profile.password}".toByteArray(StandardCharsets.UTF_8),
                Base64.NO_WRAP
            )
            renderedPayload.replaceFirst("\r\n\r\n", "\r\nProxy-Authorization: Basic $token\r\n\r\n")
        } else renderedPayload

        out.write(finalRequest.toByteArray(StandardCharsets.UTF_8))
        out.flush()

        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
        val statusLine = reader.readLine()
            ?: throw IllegalStateException("Sin respuesta HTTP del proxy")
        if (!statusLine.contains("200") && !statusLine.contains("101")) {
            throw IllegalStateException("Proxy HTTP respondió: $statusLine")
        }
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
        }
    }

    private fun wrapWithTlsSni(
        rawSocket: Socket,
        host: String,
        port: Int,
        customSni: String
    ): SSLSocket {
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val sniTarget = customSni.ifBlank { host }
        val sslSocket = factory.createSocket(rawSocket, sniTarget, port, true) as SSLSocket
        if (sniTarget.isNotBlank() && !sniTarget.matches(Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$"))) {
            val params: SSLParameters = sslSocket.sslParameters
            params.serverNames = listOf(SNIHostName(sniTarget))
            sslSocket.sslParameters = params
        }
        sslSocket.startHandshake()
        return sslSocket
    }

    private fun readFully(input: java.io.InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val n = input.read(buffer, offset, buffer.size - offset)
            if (n < 0) throw IllegalStateException("Conexión cerrada durante handshake")
            offset += n
        }
    }

    suspend fun probeProxy(profile: ProxyProfileEntity): DiagnosticProbeResult =
        withContext(Dispatchers.IO) {
            val type = profile.resolvedProxyType()
            if (type == ProxyType.DOH_DIRECT) {
                val start = System.currentTimeMillis()
                return@withContext runCatching {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(profile.dnsServer.ifBlank { "1.1.1.1" }, 443), 4000)
                    val tcpMs = System.currentTimeMillis() - start
                    val tlsStart = System.currentTimeMillis()
                    val ssl = wrapWithTlsSni(socket, profile.dnsServer.ifBlank { "1.1.1.1" }, 443, "cloudflare-dns.com")
                    val tlsMs = System.currentTimeMillis() - tlsStart
                    ssl.close()
                    DiagnosticProbeResult(
                        target = "${profile.dnsServer}:443",
                        protocol = type.shortBadge,
                        tcpLatencyMs = tcpMs,
                        tlsHandshakeMs = tlsMs,
                        reachable = true,
                        detail = "DoH HTTPS operativo (${tcpMs}ms TCP / ${tlsMs}ms TLS)"
                    )
                }.getOrElse { err ->
                    DiagnosticProbeResult(
                        target = "${profile.dnsServer}:443",
                        protocol = type.shortBadge,
                        tcpLatencyMs = null,
                        tlsHandshakeMs = null,
                        reachable = false,
                        detail = err.message ?: "Error al probar DoH"
                    )
                }
            }

            val tcpStart = System.currentTimeMillis()
            runCatching {
                val socket = Socket()
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(profile.host, profile.port), 5000)
                val tcpMs = System.currentTimeMillis() - tcpStart
                var tlsMs: Long? = null
                if (type.supportsSni || profile.port == 443) {
                    val t0 = System.currentTimeMillis()
                    val ssl = wrapWithTlsSni(socket, profile.host, profile.port, profile.sniHost)
                    tlsMs = System.currentTimeMillis() - t0
                    ssl.close()
                } else {
                    socket.close()
                }
                DiagnosticProbeResult(
                    target = "${profile.host}:${profile.port}",
                    protocol = type.shortBadge,
                    tcpLatencyMs = tcpMs,
                    tlsHandshakeMs = tlsMs,
                    reachable = true,
                    detail = if (tlsMs != null) {
                        "Alcanzable: ${tcpMs}ms TCP · ${tlsMs}ms TLS/SNI"
                    } else {
                        "Puerto abierto y alcanzable en ${tcpMs}ms"
                    }
                )
            }.getOrElse { err ->
                DiagnosticProbeResult(
                    target = "${profile.host}:${profile.port}",
                    protocol = type.shortBadge,
                    tcpLatencyMs = null,
                    tlsHandshakeMs = null,
                    reachable = false,
                    detail = err.message ?: "Host inalcanzable o tiempo agotado"
                )
            }
        }
}
