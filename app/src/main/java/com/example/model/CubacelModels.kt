package com.example.model

import android.graphics.drawable.Drawable

enum class CubacelPreset(
    val displayName: String,
    val badge: String,
    val recommendedMtu: Int,
    val keepAliveSec: Int,
    val description: String
) {
    CUBACEL_4G_LTE(
        displayName = "Cubacel 4G LTE",
        badge = "MTU 1360",
        recommendedMtu = 1360,
        keepAliveSec = 25,
        description = "Balance óptimo para LTE B3 (1800MHz): evita fragmentación GTP y mantiene sockets activos."
    ),
    CUBACEL_3G_H_PLUS(
        displayName = "Cubacel 3G / H+",
        badge = "MTU 1280",
        recommendedMtu = 1280,
        keepAliveSec = 15,
        description = "Paquetes compactos (1280B) y sondas rápidas para evitar caídas en zonas de baja cobertura."
    ),
    NAUTA_WIFI_ETECSA(
        displayName = "Nauta / Wi-Fi ETECSA",
        badge = "MTU 1420",
        recommendedMtu = 1420,
        keepAliveSec = 40,
        description = "Mayor tamaño de trama para Nauta Hogar y zonas Wi-Fi manteniendo bypass a portales .cu."
    ),
    AHORRO_EXTREMO(
        displayName = "Ahorro de Megas",
        badge = "ECO 1280",
        recommendedMtu = 1280,
        keepAliveSec = 30,
        description = "Maximiza el rendimiento por MB: bloquea fugas IPv6 y apps marcadas sin internet."
    );

    companion object {
        fun fromName(name: String): CubacelPreset =
            entries.firstOrNull { it.name == name } ?: CUBACEL_4G_LTE
    }
}

enum class SplitTunnelMode(
    val displayName: String,
    val description: String
) {
    ALL_APPS(
        displayName = "Todas las apps por el túnel",
        description = "Todo el tráfico pasa por el VPN excepto las apps marcadas como Bloqueado o Bypass."
    ),
    EXCLUDE_SELECTED(
        displayName = "Excluir seleccionadas (Bypass)",
        description = "Ideal para Cubacel: deja fuera Transfermóvil, EnZona, Todus y apps locales .cu."
    ),
    INCLUDE_ONLY(
        displayName = "Solo apps seleccionadas (Whitelist)",
        description = "Únicamente las apps elegidas usan el túnel VPN; el resto sale directo."
    );

    companion object {
        fun fromName(name: String): SplitTunnelMode =
            entries.firstOrNull { it.name == name } ?: EXCLUDE_SELECTED
    }
}

enum class AppRuleStatus(
    val label: String,
    val shortLabel: String
) {
    VPN_TUNNEL("Por Túnel VPN", "Túnel"),
    BYPASS_DIRECT("Directo (Sin VPN)", "Directo"),
    BLOCKED_NO_NET("Bloqueado (Sin Red)", "Bloqueado");

    companion object {
        fun fromName(name: String): AppRuleStatus =
            entries.firstOrNull { it.name == name } ?: VPN_TUNNEL
    }
}

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val uid: Int,
    val icon: Drawable?,
    val isCubanApp: Boolean,
    val isMessagingOrSocial: Boolean,
    val ruleStatus: AppRuleStatus
)

enum class VpnConnectionState(val label: String) {
    DISCONNECTED("Desconectado"),
    CONNECTING("Conectando túnel..."),
    CONNECTED("Túnel Activo"),
    RECONNECTING("Re-enlazando red..."),
    ERROR("Error de enlace")
}

data class VpnLogEntry(
    val timestamp: String,
    val level: LogLevel,
    val message: String
)

enum class LogLevel {
    INFO, SUCCESS, WARN, ERROR
}

data class VpnTelemetry(
    val state: VpnConnectionState = VpnConnectionState.DISCONNECTED,
    val activeProfileName: String = "Sin perfil",
    val activeProxyType: ProxyType = ProxyType.DOH_DIRECT,
    val activeMtu: Int = 1360,
    val connectedSinceMs: Long = 0L,
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val rxBytesPerSec: Long = 0L,
    val txBytesPerSec: Long = 0L,
    val dnsQueriesCount: Long = 0L,
    val blockedPacketsCount: Long = 0L,
    val activeSessionsCount: Int = 0,
    val lastPingMs: Long? = null,
    val networkTypeLabel: String = "Detectando red...",
    val isCellularCubacel: Boolean = false,
    val statusDetail: String = "Listo para iniciar VpnService"
)

data class DiagnosticProbeResult(
    val target: String,
    val protocol: String,
    val tcpLatencyMs: Long?,
    val tlsHandshakeMs: Long?,
    val reachable: Boolean,
    val detail: String
)
