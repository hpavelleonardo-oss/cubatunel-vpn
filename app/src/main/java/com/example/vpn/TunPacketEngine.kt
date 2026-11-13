package com.example.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.ParcelFileDescriptor
import com.example.data.ProxyProfileEntity
import com.example.model.LogLevel
import com.example.model.ProxyType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.net.SocketFactory

class TunPacketEngine(
    private val context: Context,
    private val tunFd: ParcelFileDescriptor,
    private val profile: ProxyProfileEntity,
    private val useDoh: Boolean,
    private val dohUrl: String,
    private val blockedUids: Set<Int>,
    private val mtu: Int,
    private val protectSocket: (Socket) -> Boolean,
    private val protectDatagram: (DatagramSocket) -> Boolean
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val writeLock = Any()
    private val outputStream = FileOutputStream(tunFd.fileDescriptor)
    private val inputStream = FileInputStream(tunFd.fileDescriptor)
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val tcpSessions = ConcurrentHashMap<SessionKey, TcpSession>()
    private val activeSessionCounter = AtomicInteger(0)
    val rxPeriodBytes = AtomicLong(0L)
    val txPeriodBytes = AtomicLong(0L)

    private val dohClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .socketFactory(ProtectedSocketFactory(protectSocket))
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    data class SessionKey(
        val srcIp: String,
        val srcPort: Int,
        val dstIp: String,
        val dstPort: Int
    )

    inner class TcpSession(
        val key: SessionKey,
        val clientIp: ByteArray,
        val serverIp: ByteArray,
        var clientNextSeq: Long,
        var serverSeq: Long = (System.nanoTime() and 0x7FFFFFFFL)
    ) {
        @Volatile var socket: Socket? = null
        @Volatile var closed = false
        val pendingWrites = java.util.concurrent.LinkedBlockingQueue<ByteArray>()
    }

    fun start(): Job {
        return scope.launch {
            val buffer = ByteArray(mtu.coerceAtLeast(1500) + 64)
            VpnStateHolder.log(
                LogLevel.INFO,
                "Motor TUN iniciado (MTU=$mtu, Bloqueados=${blockedUids.size} UIDs)"
            )
            try {
                while (isActive) {
                    val length = inputStream.read(buffer)
                    if (length < 0) break
                    if (length == 0) continue

                    txPeriodBytes.addAndGet(length.toLong())
                    VpnStateHolder.addTxBytes(length.toLong())

                    val header = PacketUtils.parseIpv4Header(buffer, length) ?: continue
                    when (header.protocol) {
                        PacketUtils.PROTO_UDP -> handleUdpPacket(buffer, length, header)
                        PacketUtils.PROTO_TCP -> handleTcpPacket(buffer, length, header)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    VpnStateHolder.log(LogLevel.WARN, "Lectura TUN finalizada: ${e.message}")
                }
            } finally {
                closeAllSessions()
            }
        }
    }

    fun activeSessionsCount(): Int = activeSessionCounter.get()

    fun stop() {
        closeAllSessions()
        scope.cancel()
        runCatching { inputStream.close() }
        runCatching { outputStream.close() }
    }

    private fun closeAllSessions() {
        tcpSessions.values.forEach { session ->
            session.closed = true
            runCatching { session.socket?.close() }
        }
        tcpSessions.clear()
        activeSessionCounter.set(0)
    }

    private fun isUidBlocked(
        protocol: Int,
        srcIp: ByteArray,
        srcPort: Int,
        dstIp: ByteArray,
        dstPort: Int
    ): Boolean {
        if (blockedUids.isEmpty()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return runCatching {
                val local = InetSocketAddress(InetAddress.getByAddress(srcIp), srcPort)
                val remote = InetSocketAddress(InetAddress.getByAddress(dstIp), dstPort)
                val uid = connectivityManager.getConnectionOwnerUid(protocol, local, remote)
                uid > 0 && blockedUids.contains(uid)
            }.getOrDefault(false)
        }
        return false
    }

    private fun handleUdpPacket(
        packet: ByteArray,
        length: Int,
        ip: PacketUtils.Ipv4Header
    ) {
        val offset = ip.headerLength
        if (length < offset + 8) return
        val srcPort = PacketUtils.readUInt16(packet, offset)
        val dstPort = PacketUtils.readUInt16(packet, offset + 2)
        val udpLen = PacketUtils.readUInt16(packet, offset + 4)
        val payloadOffset = offset + 8
        val payloadLen = (udpLen - 8).coerceAtMost(length - payloadOffset)
        if (payloadLen <= 0) return

        if (isUidBlocked(PacketUtils.PROTO_UDP, ip.srcIp, srcPort, ip.dstIp, dstPort)) {
            VpnStateHolder.incrementBlockedPackets()
            return
        }

        val payload = packet.copyOfRange(payloadOffset, payloadOffset + payloadLen)
        if (dstPort == 53) {
            VpnStateHolder.incrementDnsQueries()
            scope.launch {
                resolveDnsAndReply(ip.srcIp, srcPort, ip.dstIp, dstPort, payload)
            }
        } else {
            scope.launch {
                forwardUdpDirect(ip.srcIp, srcPort, ip.dstIp, dstPort, payload)
            }
        }
    }

    private fun resolveDnsAndReply(
        clientIp: ByteArray,
        clientPort: Int,
        dnsServerIp: ByteArray,
        dnsPort: Int,
        queryBytes: ByteArray
    ) {
        val responseBytes: ByteArray? = if (useDoh) {
            resolveViaDoh(queryBytes) ?: resolveViaUdpSocket(queryBytes, profile.dnsServer)
        } else {
            resolveViaUdpSocket(queryBytes, profile.dnsServer)
        }

        if (responseBytes != null && responseBytes.isNotEmpty()) {
            val reply = PacketUtils.buildUdpIpv4Packet(
                srcIp = dnsServerIp,
                srcPort = dnsPort,
                dstIp = clientIp,
                dstPort = clientPort,
                payload = responseBytes
            )
            writePacketToTun(reply)
        }
    }

    private fun resolveViaDoh(queryBytes: ByteArray): ByteArray? {
        return runCatching {
            val mediaType = "application/dns-message".toMediaType()
            val req = Request.Builder()
                .url(dohUrl)
                .header("Accept", "application/dns-message")
                .post(queryBytes.toRequestBody(mediaType))
                .build()
            dohClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.bytes() else null
            }
        }.getOrNull()
    }

    private fun resolveViaUdpSocket(queryBytes: ByteArray, dnsHost: String): ByteArray? {
        return runCatching {
            val targetHost = dnsHost.ifBlank { "1.1.1.1" }
            DatagramSocket().use { sock ->
                protectDatagram(sock)
                sock.soTimeout = 3500
                val addr = InetAddress.getByName(targetHost)
                sock.send(DatagramPacket(queryBytes, queryBytes.size, addr, 53))
                val rxBuf = ByteArray(1500)
                val rxPacket = DatagramPacket(rxBuf, rxBuf.size)
                sock.receive(rxPacket)
                rxBuf.copyOf(rxPacket.length)
            }
        }.getOrNull()
    }

    private fun forwardUdpDirect(
        clientIp: ByteArray,
        clientPort: Int,
        dstIp: ByteArray,
        dstPort: Int,
        payload: ByteArray
    ) {
        if (profile.resolvedProxyType() != ProxyType.DOH_DIRECT) {
            // Non-DNS UDP in proxy mode without UDPGW is dropped silently to prevent leaks
            return
        }
        runCatching {
            DatagramSocket().use { sock ->
                protectDatagram(sock)
                sock.soTimeout = 3000
                val target = InetAddress.getByAddress(dstIp)
                sock.send(DatagramPacket(payload, payload.size, target, dstPort))
                val rxBuf = ByteArray(mtu)
                val rxPacket = DatagramPacket(rxBuf, rxBuf.size)
                sock.receive(rxPacket)
                val reply = PacketUtils.buildUdpIpv4Packet(
                    srcIp = dstIp,
                    srcPort = dstPort,
                    dstIp = clientIp,
                    dstPort = clientPort,
                    payload = rxBuf.copyOf(rxPacket.length)
                )
                writePacketToTun(reply)
            }
        }
    }

    private fun handleTcpPacket(
        packet: ByteArray,
        length: Int,
        ip: PacketUtils.Ipv4Header
    ) {
        val offset = ip.headerLength
        if (length < offset + 20) return
        val srcPort = PacketUtils.readUInt16(packet, offset)
        val dstPort = PacketUtils.readUInt16(packet, offset + 2)
        val seqNum = PacketUtils.readUInt32(packet, offset + 4)
        val dataOffsetBytes = ((packet[offset + 12].toInt() ushr 4) and 0x0F) * 4
        if (length < offset + dataOffsetBytes) return
        val flags = packet[offset + 13].toInt() and 0x3F
        val payloadOffset = offset + dataOffsetBytes
        val payloadLen = (ip.totalLength - payloadOffset).coerceAtLeast(0)

        if (isUidBlocked(PacketUtils.PROTO_TCP, ip.srcIp, srcPort, ip.dstIp, dstPort)) {
            VpnStateHolder.incrementBlockedPackets()
            val rst = PacketUtils.buildTcpIpv4Packet(
                srcIp = ip.dstIp,
                srcPort = dstPort,
                dstIp = ip.srcIp,
                dstPort = srcPort,
                seqNum = 0L,
                ackNum = seqNum + 1L,
                flags = PacketUtils.TCP_RST or PacketUtils.TCP_ACK
            )
            writePacketToTun(rst)
            return
        }

        val key = SessionKey(
            srcIp = ip.srcAddressString(),
            srcPort = srcPort,
            dstIp = ip.dstAddressString(),
            dstPort = dstPort
        )

        val isSyn = (flags and PacketUtils.TCP_SYN) != 0
        val isAck = (flags and PacketUtils.TCP_ACK) != 0
        val isFin = (flags and PacketUtils.TCP_FIN) != 0
        val isRst = (flags and PacketUtils.TCP_RST) != 0

        if (isSyn && !isAck) {
            val existing = tcpSessions.remove(key)
            if (existing != null) {
                existing.closed = true
                runCatching { existing.socket?.close() }
                activeSessionCounter.decrementAndGet()
            }

            val session = TcpSession(
                key = key,
                clientIp = ip.srcIp,
                serverIp = ip.dstIp,
                clientNextSeq = seqNum + 1L
            )
            tcpSessions[key] = session
            activeSessionCounter.incrementAndGet()

            scope.launch {
                openProxySession(session)
            }
            return
        }

        val session = tcpSessions[key] ?: return

        if (isRst || isFin) {
            session.closed = true
            tcpSessions.remove(key)
            activeSessionCounter.decrementAndGet()
            runCatching { session.socket?.close() }
            if (isFin) {
                session.clientNextSeq = seqNum + 1L
                val finAck = PacketUtils.buildTcpIpv4Packet(
                    srcIp = session.serverIp,
                    srcPort = key.dstPort,
                    dstIp = session.clientIp,
                    dstPort = key.srcPort,
                    seqNum = session.serverSeq,
                    ackNum = session.clientNextSeq,
                    flags = PacketUtils.TCP_FIN or PacketUtils.TCP_ACK
                )
                writePacketToTun(finAck)
            }
            return
        }

        if (payloadLen > 0) {
            val payload = packet.copyOfRange(payloadOffset, payloadOffset + payloadLen)
            session.clientNextSeq = seqNum + payloadLen.toLong()
            val ackPacket = PacketUtils.buildTcpIpv4Packet(
                srcIp = session.serverIp,
                srcPort = key.dstPort,
                dstIp = session.clientIp,
                dstPort = key.srcPort,
                seqNum = session.serverSeq,
                ackNum = session.clientNextSeq,
                flags = PacketUtils.TCP_ACK
            )
            writePacketToTun(ackPacket)

            val sock = session.socket
            if (sock != null && !sock.isClosed) {
                scope.launch {
                    runCatching {
                        sock.getOutputStream().write(payload)
                        sock.getOutputStream().flush()
                    }.onFailure {
                        closeSessionWithRst(session)
                    }
                }
            } else {
                session.pendingWrites.offer(payload)
            }
        }
    }

    private fun openProxySession(session: TcpSession) {
        val key = session.key
        try {
            val socket = ProxyDialer.connectThroughProxy(
                profile = profile,
                targetHost = key.dstIp,
                targetPort = key.dstPort,
                timeoutMs = 7000,
                protectSocket = protectSocket
            )
            if (session.closed) {
                runCatching { socket.close() }
                return
            }
            session.socket = socket

            // Send SYN+ACK to TUN client
            val synAck = PacketUtils.buildTcpIpv4Packet(
                srcIp = session.serverIp,
                srcPort = key.dstPort,
                dstIp = session.clientIp,
                dstPort = key.srcPort,
                seqNum = session.serverSeq,
                ackNum = session.clientNextSeq,
                flags = PacketUtils.TCP_SYN or PacketUtils.TCP_ACK
            )
            session.serverSeq += 1L
            writePacketToTun(synAck)

            // Flush any queued early writes
            while (!session.pendingWrites.isEmpty()) {
                val queued = session.pendingWrites.poll() ?: break
                socket.getOutputStream().write(queued)
            }
            socket.getOutputStream().flush()

            // Stream upstream socket -> TUN
            val maxChunk = (mtu - 60).coerceIn(512, 1400)
            val rxBuf = ByteArray(maxChunk)
            val input = socket.getInputStream()
            while (!session.closed) {
                val n = input.read(rxBuf)
                if (n <= 0) break
                val chunk = rxBuf.copyOf(n)
                val dataPkt = PacketUtils.buildTcpIpv4Packet(
                    srcIp = session.serverIp,
                    srcPort = key.dstPort,
                    dstIp = session.clientIp,
                    dstPort = key.srcPort,
                    seqNum = session.serverSeq,
                    ackNum = session.clientNextSeq,
                    flags = PacketUtils.TCP_PSH or PacketUtils.TCP_ACK,
                    payload = chunk
                )
                session.serverSeq += n.toLong()
                writePacketToTun(dataPkt)
            }

            if (!session.closed) {
                val fin = PacketUtils.buildTcpIpv4Packet(
                    srcIp = session.serverIp,
                    srcPort = key.dstPort,
                    dstIp = session.clientIp,
                    dstPort = key.srcPort,
                    seqNum = session.serverSeq,
                    ackNum = session.clientNextSeq,
                    flags = PacketUtils.TCP_FIN or PacketUtils.TCP_ACK
                )
                writePacketToTun(fin)
            }
        } catch (e: Exception) {
            closeSessionWithRst(session)
        } finally {
            session.closed = true
            if (tcpSessions.remove(key) != null) {
                activeSessionCounter.decrementAndGet()
            }
            runCatching { session.socket?.close() }
        }
    }

    private fun closeSessionWithRst(session: TcpSession) {
        if (session.closed) return
        session.closed = true
        val rst = PacketUtils.buildTcpIpv4Packet(
            srcIp = session.serverIp,
            srcPort = session.key.dstPort,
            dstIp = session.clientIp,
            dstPort = session.key.srcPort,
            seqNum = session.serverSeq,
            ackNum = session.clientNextSeq,
            flags = PacketUtils.TCP_RST or PacketUtils.TCP_ACK
        )
        writePacketToTun(rst)
        if (tcpSessions.remove(session.key) != null) {
            activeSessionCounter.decrementAndGet()
        }
        runCatching { session.socket?.close() }
    }

    private fun writePacketToTun(packet: ByteArray) {
        synchronized(writeLock) {
            runCatching {
                outputStream.write(packet)
                rxPeriodBytes.addAndGet(packet.size.toLong())
                VpnStateHolder.addRxBytes(packet.size.toLong())
            }
        }
    }

    private class ProtectedSocketFactory(
        private val protectFn: (Socket) -> Boolean
    ) : SocketFactory() {
        override fun createSocket(): Socket = Socket().also { protectFn(it) }

        override fun createSocket(host: String?, port: Int): Socket =
            Socket().also {
                protectFn(it)
                if (host != null) it.connect(InetSocketAddress(host, port))
            }

        override fun createSocket(
            host: String?,
            port: Int,
            localHost: InetAddress?,
            localPort: Int
        ): Socket = Socket().also {
            protectFn(it)
            if (localHost != null) it.bind(InetSocketAddress(localHost, localPort))
            if (host != null) it.connect(InetSocketAddress(host, port))
        }

        override fun createSocket(host: InetAddress?, port: Int): Socket =
            Socket().also {
                protectFn(it)
                if (host != null) it.connect(InetSocketAddress(host, port))
            }

        override fun createSocket(
            address: InetAddress?,
            port: Int,
            localAddress: InetAddress?,
            localPort: Int
        ): Socket = Socket().also {
            protectFn(it)
            if (localAddress != null) it.bind(InetSocketAddress(localAddress, localPort))
            if (address != null) it.connect(InetSocketAddress(address, port))
        }
    }
}
