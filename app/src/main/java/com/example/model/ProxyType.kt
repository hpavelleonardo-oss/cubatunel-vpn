package com.example.model

enum class ProxyType(
    val displayName: String,
    val shortBadge: String,
    val defaultPort: Int,
    val description: String,
    val cubacelTip: String,
    val supportsAuth: Boolean = true,
    val supportsSni: Boolean = false,
    val supportsPayload: Boolean = false,
    val supportsMethodOrFlow: Boolean = false,
    val supportsDnsTunnel: Boolean = false,
    val requiresRemoteServer: Boolean = true
) {
    SOCKS5(
        displayName = "SOCKS5 (TCP + UDP)",
        shortBadge = "SOCKS5",
        defaultPort = 1080,
        description = "Proxy estándar RFC 1928 con soporte de autenticación y reenvío UDP/DNS.",
        cubacelTip = "Excelente estabilidad en 4G LTE con MTU 1360 y Keep-Alive 25s.",
        supportsAuth = true
    ),
    SOCKS4(
        displayName = "SOCKS4 / SOCKS4a",
        shortBadge = "SOCKS4a",
        defaultPort = 1080,
        description = "Protocolo ligero compatible con servidores antiguos y resolución remota 4a.",
        cubacelTip = "Menor sobrecarga de cabecera para conexiones 3G/H+ con latencia alta.",
        supportsAuth = false
    ),
    HTTP_CONNECT(
        displayName = "HTTP / HTTPS CONNECT",
        shortBadge = "HTTP",
        defaultPort = 8080,
        description = "Túnel TCP mediante método CONNECT sobre puertos web 8080/3128/80.",
        cubacelTip = "Ideal cuando los puertos no web están restringidos o tras proxies corporativos.",
        supportsAuth = true,
        supportsPayload = true
    ),
    HTTP_PAYLOAD(
        displayName = "HTTP Inyector + Payload",
        shortBadge = "PAYLOAD",
        defaultPort = 80,
        description = "Inyección personalizada de cabeceras HTTP, Host, X-Online-Host y Keep-Alive.",
        cubacelTip = "Usa cabeceras split/[crlf] y Connection: Keep-Alive para evitar cortes ETECSA.",
        supportsAuth = true,
        supportsPayload = true
    ),
    SSL_TLS_SNI(
        displayName = "SSL / TLS + SNI Spoofing",
        shortBadge = "TLS/SNI",
        defaultPort = 443,
        description = "Cifrado TLS 1.2/1.3 con Server Name Indication (SNI) y ALPN configurable.",
        cubacelTip = "Camufla el tráfico como HTTPS legítimo en puerto 443 con SNI optimizado.",
        supportsAuth = true,
        supportsSni = true,
        supportsPayload = true
    ),
    WS_TLS_CDN(
        displayName = "WebSocket + TLS (CDN)",
        shortBadge = "WS+TLS",
        defaultPort = 443,
        description = "Túnel encapsulado sobre WebSocket + TLS detrás de CDN (Cloudflare/Fronting).",
        cubacelTip = "Muy resistente a throttling en Cubacel gracias al tráfico WebSocket estándar.",
        supportsAuth = true,
        supportsSni = true,
        supportsPayload = true
    ),
    SSH_TUNNEL(
        displayName = "SSH Tunnel (Direct / SSL)",
        shortBadge = "SSH",
        defaultPort = 22,
        description = "Reenvío dinámico de puertos sobre SSHv2 con soporte opcional de Payload o Stunnel.",
        cubacelTip = "Usa puerto 443 con SSL o 80 con Payload si el puerto 22 sufre pérdida de paquetes.",
        supportsAuth = true,
        supportsSni = true,
        supportsPayload = true
    ),
    SHADOWSOCKS(
        displayName = "Shadowsocks (AEAD)",
        shortBadge = "SS-AEAD",
        defaultPort = 8388,
        description = "Proxy cifrado ligero (AES-256-GCM / ChaCha20-Poly1305) de baja huella.",
        cubacelTip = "ChaCha20-Poly1305 consume menos batería y acelera la respuesta en móviles.",
        supportsAuth = false,
        supportsMethodOrFlow = true
    ),
    VLESS(
        displayName = "VLESS (TLS / Reality / WS)",
        shortBadge = "VLESS",
        defaultPort = 443,
        description = "Transporte moderno sin doble cifrado, compatible con XTLS Reality, WS y gRPC.",
        cubacelTip = "Mínimo consumo de datos móviles (sin overhead extra) en paquetes Cubacel.",
        supportsAuth = false,
        supportsSni = true,
        supportsMethodOrFlow = true
    ),
    VMESS(
        displayName = "VMess (WS / TCP / gRPC)",
        shortBadge = "VMESS",
        defaultPort = 443,
        description = "Protocolo cifrado V2Ray con transporte WebSocket/TLS y AlterID 0.",
        cubacelTip = "Configura transporte WS + TLS en puerto 443 para máxima estabilidad nocturna.",
        supportsAuth = false,
        supportsSni = true,
        supportsMethodOrFlow = true
    ),
    TROJAN(
        displayName = "Trojan (TLS 1.3)",
        shortBadge = "TROJAN",
        defaultPort = 443,
        description = "Simula tráfico HTTPS estándar indistinguible con autenticación por contraseña.",
        cubacelTip = "Excelente rendimiento en 4G Cubacel por su handshake TLS rápido de 1-RTT.",
        supportsAuth = true,
        supportsSni = true,
        supportsMethodOrFlow = true
    ),
    SLOW_DNS(
        displayName = "SlowDNS / DNS Tunnel",
        shortBadge = "SLOWDNS",
        defaultPort = 53,
        description = "Encapsulamiento sobre consultas UDP/53 o DoH para redes muy restringidas.",
        cubacelTip = "Útil de respaldo cuando TCP está bloqueado; ajusta MTU a 1280 bytes.",
        supportsAuth = false,
        supportsDnsTunnel = true
    ),
    DOH_DIRECT(
        displayName = "DNS Optimizado (DoH Anti-Bloqueo)",
        shortBadge = "DNS-DoH",
        defaultPort = 443,
        description = "VpnService local sin servidor remoto: cifra consultas DNS vía HTTPS y activa el Cortafuegos Split Túnel.",
        cubacelTip = "Funciona de inmediato sin proxy externo: acelera resolución y ahorra megas.",
        supportsAuth = false,
        requiresRemoteServer = false
    );

    companion object {
        fun fromName(name: String): ProxyType =
            entries.firstOrNull { it.name == name } ?: SOCKS5
    }
}
