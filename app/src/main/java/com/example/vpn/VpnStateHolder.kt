package com.example.vpn

import com.example.model.LogLevel
import com.example.model.ProxyType
import com.example.model.VpnConnectionState
import com.example.model.VpnLogEntry
import com.example.model.VpnTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VpnStateHolder {

    private val _telemetry = MutableStateFlow(VpnTelemetry())
    val telemetry: StateFlow<VpnTelemetry> = _telemetry.asStateFlow()

    private val _logs = MutableStateFlow<List<VpnLogEntry>>(emptyList())
    val logs: StateFlow<List<VpnLogEntry>> = _logs.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun updateState(
        state: VpnConnectionState,
        detail: String,
        profileName: String? = null,
        proxyType: ProxyType? = null,
        mtu: Int? = null
    ) {
        _telemetry.update { current ->
            val connectedSince = when {
                state == VpnConnectionState.CONNECTED && current.connectedSinceMs == 0L ->
                    System.currentTimeMillis()
                state == VpnConnectionState.DISCONNECTED -> 0L
                else -> current.connectedSinceMs
            }
            current.copy(
                state = state,
                statusDetail = detail,
                activeProfileName = profileName ?: current.activeProfileName,
                activeProxyType = proxyType ?: current.activeProxyType,
                activeMtu = mtu ?: current.activeMtu,
                connectedSinceMs = connectedSince,
                rxBytesPerSec = if (state == VpnConnectionState.DISCONNECTED) 0L else current.rxBytesPerSec,
                txBytesPerSec = if (state == VpnConnectionState.DISCONNECTED) 0L else current.txBytesPerSec,
                activeSessionsCount = if (state == VpnConnectionState.DISCONNECTED) 0 else current.activeSessionsCount
            )
        }
    }

    fun updateNetworkInfo(label: String, isCellular: Boolean) {
        _telemetry.update {
            it.copy(networkTypeLabel = label, isCellularCubacel = isCellular)
        }
    }

    fun updatePing(pingMs: Long?) {
        _telemetry.update { it.copy(lastPingMs = pingMs) }
    }

    fun addRxBytes(bytes: Long) {
        if (bytes <= 0) return
        _telemetry.update { it.copy(rxBytes = it.rxBytes + bytes) }
    }

    fun addTxBytes(bytes: Long) {
        if (bytes <= 0) return
        _telemetry.update { it.copy(txBytes = it.txBytes + bytes) }
    }

    fun updateThroughput(rxBps: Long, txBps: Long, activeSessions: Int) {
        _telemetry.update {
            it.copy(
                rxBytesPerSec = rxBps,
                txBytesPerSec = txBps,
                activeSessionsCount = activeSessions
            )
        }
    }

    fun incrementDnsQueries() {
        _telemetry.update { it.copy(dnsQueriesCount = it.dnsQueriesCount + 1) }
    }

    fun incrementBlockedPackets() {
        _telemetry.update { it.copy(blockedPacketsCount = it.blockedPacketsCount + 1) }
    }

    fun resetSessionCounters() {
        _telemetry.update {
            it.copy(
                rxBytes = 0L,
                txBytes = 0L,
                rxBytesPerSec = 0L,
                txBytesPerSec = 0L,
                dnsQueriesCount = 0L,
                blockedPacketsCount = 0L,
                activeSessionsCount = 0,
                connectedSinceMs = 0L
            )
        }
    }

    fun log(level: LogLevel, message: String) {
        val stamp = synchronized(timeFormat) { timeFormat.format(Date()) }
        val entry = VpnLogEntry(timestamp = stamp, level = level, message = message)
        _logs.update { current ->
            (listOf(entry) + current).take(120)
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
