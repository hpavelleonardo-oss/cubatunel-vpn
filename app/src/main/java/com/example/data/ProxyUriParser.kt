package com.example.data

import android.net.Uri
import android.util.Base64
import com.example.model.ProxyType
import org.json.JSONObject

object ProxyUriParser {

    fun parseUri(rawInput: String): ProxyProfileEntity? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            when {
                trimmed.startsWith("vmess://", ignoreCase = true) -> parseVmess(trimmed)
                trimmed.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(trimmed)
                trimmed.startsWith("vless://", ignoreCase = true) -> parseVlessOrTrojan(trimmed, ProxyType.VLESS)
                trimmed.startsWith("trojan://", ignoreCase = true) -> parseVlessOrTrojan(trimmed, ProxyType.TROJAN)
                trimmed.startsWith("socks5://", ignoreCase = true) ||
                    trimmed.startsWith("socks://", ignoreCase = true) -> parseStandardUri(trimmed, ProxyType.SOCKS5)
                trimmed.startsWith("socks4://", ignoreCase = true) -> parseStandardUri(trimmed, ProxyType.SOCKS4)
                trimmed.startsWith("https://", ignoreCase = true) -> parseStandardUri(trimmed, ProxyType.SSL_TLS_SNI)
                trimmed.startsWith("http://", ignoreCase = true) -> parseStandardUri(trimmed, ProxyType.HTTP_CONNECT)
                trimmed.startsWith("ssh://", ignoreCase = true) -> parseStandardUri(trimmed, ProxyType.SSH_TUNNEL)
                trimmed.contains(":") -> parseHostPort(trimmed)
                else -> null
            }
        }.getOrNull()
    }

    private fun parseStandardUri(raw: String, type: ProxyType): ProxyProfileEntity? {
        val uri = Uri.parse(raw)
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else type.defaultPort
        val userInfo = uri.userInfo.orEmpty()
        val parts = userInfo.split(":", limit = 2)
        val user = parts.getOrNull(0).orEmpty()
        val pass = parts.getOrNull(1).orEmpty()
        val label = uri.fragment?.takeIf { it.isNotBlank() } ?: "${type.shortBadge} $host"
        val sni = uri.getQueryParameter("sni").orEmpty()
        return ProxyProfileEntity(
            name = label,
            proxyType = type.name,
            host = host,
            port = port,
            username = user,
            password = pass,
            sniHost = sni,
            isCubacelOptimized = true
        )
    }

    private fun parseVlessOrTrojan(raw: String, type: ProxyType): ProxyProfileEntity? {
        val uri = Uri.parse(raw)
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val auth = uri.userInfo.orEmpty()
        val label = uri.fragment?.takeIf { it.isNotBlank() } ?: "${type.shortBadge} $host"
        val sni = uri.getQueryParameter("sni") ?: uri.getQueryParameter("peer") ?: ""
        val path = uri.getQueryParameter("path") ?: "/ws"
        val flow = uri.getQueryParameter("flow") ?: uri.getQueryParameter("type") ?: "ws"
        return ProxyProfileEntity(
            name = label,
            proxyType = type.name,
            host = host,
            port = port,
            password = if (type == ProxyType.TROJAN) auth else "",
            uuidOrKey = if (type == ProxyType.VLESS) auth else "",
            sniHost = sni,
            wsPath = path,
            encryptionMethod = flow,
            isCubacelOptimized = true
        )
    }

    private fun parseShadowsocks(raw: String): ProxyProfileEntity? {
        val withoutScheme = raw.removePrefix("ss://").removePrefix("SS://")
        val fragmentSplit = withoutScheme.split("#", limit = 2)
        val label = fragmentSplit.getOrNull(1)?.let { Uri.decode(it) }?.takeIf { it.isNotBlank() }
        val mainPart = fragmentSplit[0].substringBefore("?")

        if (mainPart.contains("@")) {
            val (userPart, hostPort) = mainPart.split("@", limit = 2)
            val decodedAuth = runCatching {
                val padded = userPart.replace('-', '+').replace('_', '/')
                String(Base64.decode(padded, Base64.DEFAULT or Base64.NO_WRAP))
            }.getOrDefault(userPart)
            val method = decodedAuth.substringBefore(":", "chacha20-poly1305")
            val pass = decodedAuth.substringAfter(":", "")
            val host = hostPort.substringBefore(":")
            val port = hostPort.substringAfter(":", "8388").toIntOrNull() ?: 8388
            return ProxyProfileEntity(
                name = label ?: "Shadowsocks $host",
                proxyType = ProxyType.SHADOWSOCKS.name,
                host = host,
                port = port,
                encryptionMethod = method,
                uuidOrKey = pass,
                isCubacelOptimized = true
            )
        } else {
            val decoded = String(Base64.decode(mainPart, Base64.DEFAULT))
            val (auth, hostPort) = decoded.split("@", limit = 2)
            val method = auth.substringBefore(":")
            val pass = auth.substringAfter(":")
            val host = hostPort.substringBefore(":")
            val port = hostPort.substringAfter(":", "8388").toIntOrNull() ?: 8388
            return ProxyProfileEntity(
                name = label ?: "Shadowsocks $host",
                proxyType = ProxyType.SHADOWSOCKS.name,
                host = host,
                port = port,
                encryptionMethod = method,
                uuidOrKey = pass,
                isCubacelOptimized = true
            )
        }
    }

    private fun parseVmess(raw: String): ProxyProfileEntity? {
        val b64 = raw.removePrefix("vmess://").removePrefix("VMESS://").trim()
        val jsonStr = String(Base64.decode(b64, Base64.DEFAULT))
        val obj = JSONObject(jsonStr)
        val host = obj.optString("add")
        if (host.isBlank()) return null
        val port = obj.optInt("port", 443)
        val ps = obj.optString("ps").ifBlank { "VMess $host" }
        val id = obj.optString("id")
        val sni = obj.optString("sni").ifBlank { obj.optString("host") }
        val path = obj.optString("path").ifBlank { "/ws" }
        val net = obj.optString("net").ifBlank { "ws" }
        return ProxyProfileEntity(
            name = ps,
            proxyType = ProxyType.VMESS.name,
            host = host,
            port = port,
            uuidOrKey = id,
            sniHost = sni,
            wsPath = path,
            encryptionMethod = net,
            isCubacelOptimized = true
        )
    }

    private fun parseHostPort(raw: String): ProxyProfileEntity? {
        val parts = raw.split(":")
        if (parts.size < 2) return null
        val host = parts[0].trim()
        val port = parts[1].trim().toIntOrNull() ?: return null
        if (host.isBlank() || port !in 1..65535) return null
        return ProxyProfileEntity(
            name = "Proxy $host:$port",
            proxyType = ProxyType.SOCKS5.name,
            host = host,
            port = port,
            username = parts.getOrNull(2).orEmpty(),
            password = parts.getOrNull(3).orEmpty(),
            isCubacelOptimized = true
        )
    }

    fun exportUri(profile: ProxyProfileEntity): String {
        val label = Uri.encode(profile.name)
        val auth = if (profile.username.isNotBlank()) {
            "${Uri.encode(profile.username)}:${Uri.encode(profile.password)}@"
        } else ""
        return when (profile.resolvedProxyType()) {
            ProxyType.SOCKS5 -> "socks5://$auth${profile.host}:${profile.port}#$label"
            ProxyType.SOCKS4 -> "socks4://${profile.host}:${profile.port}#$label"
            ProxyType.HTTP_CONNECT, ProxyType.HTTP_PAYLOAD ->
                "http://$auth${profile.host}:${profile.port}#$label"
            ProxyType.SSL_TLS_SNI, ProxyType.WS_TLS_CDN ->
                "https://$auth${profile.host}:${profile.port}?sni=${Uri.encode(profile.sniHost)}#$label"
            ProxyType.SSH_TUNNEL -> "ssh://$auth${profile.host}:${profile.port}#$label"
            ProxyType.TROJAN ->
                "trojan://${Uri.encode(profile.password)}@${profile.host}:${profile.port}?sni=${Uri.encode(profile.sniHost)}#$label"
            ProxyType.VLESS ->
                "vless://${Uri.encode(profile.uuidOrKey)}@${profile.host}:${profile.port}?sni=${Uri.encode(profile.sniHost)}&path=${Uri.encode(profile.wsPath)}#$label"
            ProxyType.SHADOWSOCKS -> {
                val creds = Base64.encodeToString(
                    "${profile.encryptionMethod}:${profile.uuidOrKey}".toByteArray(),
                    Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
                )
                "ss://$creds@${profile.host}:${profile.port}#$label"
            }
            ProxyType.VMESS -> {
                val obj = JSONObject().apply {
                    put("v", "2")
                    put("ps", profile.name)
                    put("add", profile.host)
                    put("port", profile.port)
                    put("id", profile.uuidOrKey)
                    put("net", profile.encryptionMethod)
                    put("path", profile.wsPath)
                    put("sni", profile.sniHost)
                }
                "vmess://" + Base64.encodeToString(obj.toString().toByteArray(), Base64.NO_WRAP)
            }
            ProxyType.SLOW_DNS -> "slowdns://${profile.host}:${profile.port}?ns=${Uri.encode(profile.dnsServer)}#$label"
            ProxyType.DOH_DIRECT -> "doh://${profile.dnsServer}#${label}"
        }
    }
}
