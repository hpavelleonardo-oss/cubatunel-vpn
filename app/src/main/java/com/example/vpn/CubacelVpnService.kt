package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.IpPrefix
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.CubacelSettings
import com.example.data.PreferencesRepository
import com.example.data.ProxyProfileEntity
import com.example.model.AppRuleStatus
import com.example.model.LogLevel
import com.example.model.ProxyType
import com.example.model.SplitTunnelMode
import com.example.model.VpnConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetAddress

class CubacelVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.example.vpn.ACTION_START"
        const val ACTION_STOP = "com.example.vpn.ACTION_STOP"
        const val ACTION_RELOAD = "com.example.vpn.ACTION_RELOAD"
        private const val CHANNEL_ID = "cubatunnel_vpn_channel"
        private const val NOTIF_ID = 4401

        fun startVpn(context: Context) {
            val intent = Intent(context, CubacelVpnService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopVpn(context: Context) {
            val intent = Intent(context, CubacelVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun reloadVpn(context: Context) {
            val intent = Intent(context, CubacelVpnService::class.java).apply {
                action = ACTION_RELOAD
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var tunInterface: ParcelFileDescriptor? = null
    private var packetEngine: TunPacketEngine? = null
    private var engineJob: Job? = null
    private var telemetryTickerJob: Job? = null
    private var keepAliveJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var lastCellularState: Boolean? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerNetworkMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTunnel(stopSelfAfter = true)
                return START_NOT_STICKY
            }
            ACTION_RELOAD -> {
                serviceScope.launch {
                    stopTunnel(stopSelfAfter = false)
                    startTunnelInternal()
                }
            }
            else -> {
                // Handles ACTION_START and system Always-On VPN launches
                promoteToForeground("Iniciando túnel Cubacel...")
                serviceScope.launch {
                    startTunnelInternal()
                }
            }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        VpnStateHolder.log(LogLevel.WARN, "Permiso VPN revocado por el sistema")
        stopTunnel(stopSelfAfter = true)
        super.onRevoke()
    }

    override fun onDestroy() {
        unregisterNetworkMonitor()
        stopTunnel(stopSelfAfter = false)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun promoteToForeground(subtitle: String) {
        val notification = buildNotification(subtitle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private suspend fun startTunnelInternal() {
        VpnStateHolder.resetSessionCounters()
        val db = AppDatabase.getInstance(applicationContext)
        AppDatabase.ensureDefaultStarterProfiles(db.proxyProfileDao())

        val prefsRepo = PreferencesRepository(applicationContext)
        val settings = prefsRepo.getSnapshot()
        var profile = db.proxyProfileDao().getActiveProfile()
            ?: db.proxyProfileDao().getAllProfilesSnapshot().firstOrNull()

        if (profile == null) {
            VpnStateHolder.updateState(
                VpnConnectionState.ERROR,
                "No hay perfiles de proxy configurados"
            )
            stopSelf()
            return
        }

        VpnStateHolder.updateState(
            state = VpnConnectionState.CONNECTING,
            detail = "Preparando ${profile.resolvedProxyType().shortBadge} (${profile.name})...",
            profileName = profile.name,
            proxyType = profile.resolvedProxyType(),
            mtu = resolveEffectiveMtu(profile, settings)
        )
        VpnStateHolder.log(
            LogLevel.INFO,
            "Conectando perfil '${profile.name}' [${profile.resolvedProxyType().shortBadge}]"
        )

        // Probe primary proxy & optional Auto-Failover
        if (profile.resolvedProxyType().requiresRemoteServer) {
            val probe = ProxyDialer.probeProxy(profile)
            if (!probe.reachable) {
                VpnStateHolder.log(
                    LogLevel.WARN,
                    "Servidor ${profile.host}:${profile.port} no responde (${probe.detail})"
                )
                if (settings.autoFailoverProxy) {
                    val fallback = findHealthyFailover(db.proxyProfileDao().getAllProfilesSnapshot(), profile.id)
                    if (fallback != null) {
                        profile = fallback
                        db.proxyProfileDao().setActiveProfile(fallback.id)
                        VpnStateHolder.log(
                            LogLevel.SUCCESS,
                            "Auto-Failover activado -> ${fallback.name}"
                        )
                    }
                }
            } else {
                VpnStateHolder.updatePing(probe.tcpLatencyMs)
                db.proxyProfileDao().updateLatency(profile.id, probe.tcpLatencyMs)
                VpnStateHolder.log(LogLevel.SUCCESS, "Sonda exitosa: ${probe.detail}")
            }
        } else {
            val probe = ProxyDialer.probeProxy(profile)
            VpnStateHolder.updatePing(probe.tcpLatencyMs)
            VpnStateHolder.log(LogLevel.INFO, "Modo DoH Local: ${probe.detail}")
        }

        val effectiveMtu = resolveEffectiveMtu(profile, settings)
        val splitRules = db.appSplitRuleDao().getAllRulesSnapshot()
            .associate { it.packageName to AppRuleStatus.fromName(it.ruleStatus) }

        val blockedUids = resolveBlockedUids(splitRules)
        val tun = buildAndEstablishVpn(profile, settings, splitRules, effectiveMtu)
        if (tun == null) {
            VpnStateHolder.updateState(
                VpnConnectionState.ERROR,
                "El sistema rechazó la creación de la interfaz TUN"
            )
            VpnStateHolder.log(LogLevel.ERROR, "Builder.establish() devolvió null")
            stopSelf()
            return
        }

        tunInterface = tun
        val engine = TunPacketEngine(
            context = applicationContext,
            tunFd = tun,
            profile = profile,
            useDoh = settings.useDohDns,
            dohUrl = settings.dohProviderUrl,
            blockedUids = blockedUids,
            mtu = effectiveMtu,
            protectSocket = { sock -> protect(sock) },
            protectDatagram = { dg -> protect(dg) }
        )
        packetEngine = engine
        engineJob = engine.start()

        VpnStateHolder.updateState(
            state = VpnConnectionState.CONNECTED,
            detail = "${profile.resolvedProxyType().shortBadge} · MTU $effectiveMtu · ${settings.splitTunnelMode.displayName}",
            profileName = profile.name,
            proxyType = profile.resolvedProxyType(),
            mtu = effectiveMtu
        )
        VpnStateHolder.log(
            LogLevel.SUCCESS,
            "VpnService ACTIVO · Túnel establecido (MTU $effectiveMtu, Bypass .cu=${settings.bypassCuRoutes})"
        )

        startTelemetryLoop(profile)
        startKeepAliveLoop(profile)
    }

    private suspend fun findHealthyFailover(
        candidates: List<ProxyProfileEntity>,
        excludeId: Long
    ): ProxyProfileEntity? {
        for (candidate in candidates) {
            if (candidate.id == excludeId) continue
            val res = ProxyDialer.probeProxy(candidate)
            if (res.reachable) {
                VpnStateHolder.updatePing(res.tcpLatencyMs)
                return candidate
            }
        }
        return null
    }

    private fun resolveEffectiveMtu(profile: ProxyProfileEntity, settings: CubacelSettings): Int {
        if (!settings.adaptiveMtu) return profile.customMtu
        val netLabel = VpnStateHolder.telemetry.value.networkTypeLabel
        return when {
            netLabel.contains("3G", ignoreCase = true) -> 1280
            netLabel.contains("4G", ignoreCase = true) -> 1360
            netLabel.contains("Wi-Fi", ignoreCase = true) -> 1420
            else -> settings.cubacelPreset.recommendedMtu
        }
    }

    private fun resolveBlockedUids(rules: Map<String, AppRuleStatus>): Set<Int> {
        val pm = packageManager
        val uids = mutableSetOf<Int>()
        for ((pkg, status) in rules) {
            if (status == AppRuleStatus.BLOCKED_NO_NET) {
                runCatching {
                    val info = pm.getApplicationInfo(pkg, 0)
                    uids.add(info.uid)
                }
            }
        }
        return uids
    }

    private fun buildAndEstablishVpn(
        profile: ProxyProfileEntity,
        settings: CubacelSettings,
        rules: Map<String, AppRuleStatus>,
        mtu: Int
    ): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession("CubaTunnel · ${profile.name}")
            .setMtu(mtu)
            .addAddress("10.8.0.2", 32)
            .allowBypass()

        if (!settings.blockIpv6Leaks) {
            builder.addAddress("fd00:10::2", 128)
        }

        val isDohOnly = profile.resolvedProxyType() == ProxyType.DOH_DIRECT
        if (isDohOnly) {
            // Route only virtual DNS IP through TUN so all apps retain native speed while DNS goes via DoH
            builder.addDnsServer("10.8.0.1")
            builder.addRoute("10.8.0.1", 32)
        } else {
            builder.addDnsServer(profile.dnsServer.ifBlank { "1.1.1.1" })
            builder.addRoute("0.0.0.0", 0)
            if (settings.bypassCuRoutes && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val cubanPrefixes = listOf(
                    "152.206.0.0" to 16,
                    "181.225.0.0" to 16,
                    "190.6.64.0" to 18,
                    "190.15.144.0" to 20,
                    "200.55.128.0" to 18,
                    "10.0.0.0" to 8
                )
                for ((ip, prefixLen) in cubanPrefixes) {
                    runCatching {
                        builder.excludeRoute(IpPrefix(InetAddress.getByName(ip), prefixLen))
                    }
                }
            }
        }

        // Apply Split Tunneling (mutually exclusive allowed vs disallowed lists per Android VpnService spec)
        when (settings.splitTunnelMode) {
            SplitTunnelMode.INCLUDE_ONLY -> {
                val allowed = rules.filterValues { it == AppRuleStatus.VPN_TUNNEL }.keys
                var addedCount = 0
                for (pkg in allowed) {
                    if (pkg == packageName) continue
                    try {
                        packageManager.getPackageInfo(pkg, 0)
                        builder.addAllowedApplication(pkg)
                        addedCount++
                    } catch (_: PackageManager.NameNotFoundException) {
                    }
                }
                if (addedCount == 0) {
                    // Fallback: exclude self so we never deadlock if whitelist is empty
                    runCatching { builder.addDisallowedApplication(packageName) }
                }
                VpnStateHolder.log(LogLevel.INFO, "Split Túnel Whitelist: $addedCount apps en túnel")
            }
            SplitTunnelMode.EXCLUDE_SELECTED -> {
                runCatching { builder.addDisallowedApplication(packageName) }
                val bypassed = rules.filterValues { it == AppRuleStatus.BYPASS_DIRECT }.keys
                var bypassCount = 0
                for (pkg in bypassed) {
                    try {
                        packageManager.getPackageInfo(pkg, 0)
                        builder.addDisallowedApplication(pkg)
                        bypassCount++
                    } catch (_: PackageManager.NameNotFoundException) {
                    }
                }
                VpnStateHolder.log(LogLevel.INFO, "Split Túnel Bypass: $bypassCount apps directas por Cubacel")
            }
            SplitTunnelMode.ALL_APPS -> {
                runCatching { builder.addDisallowedApplication(packageName) }
                VpnStateHolder.log(LogLevel.INFO, "Split Túnel Global: todas las apps por el túnel")
            }
        }

        return runCatching { builder.establish() }.getOrNull()
    }

    private fun startTelemetryLoop(profile: ProxyProfileEntity) {
        telemetryTickerJob?.cancel()
        telemetryTickerJob = serviceScope.launch {
            while (isActive) {
                delay(1000L)
                val engine = packetEngine ?: break
                val rxBps = engine.rxPeriodBytes.getAndSet(0L)
                val txBps = engine.txPeriodBytes.getAndSet(0L)
                VpnStateHolder.updateThroughput(rxBps, txBps, engine.activeSessionsCount())

                val notifText = "${profile.resolvedProxyType().shortBadge} · ↓ ${formatRate(rxBps)} · ↑ ${formatRate(txBps)}"
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIF_ID, buildNotification(notifText))
            }
        }
    }

    private fun startKeepAliveLoop(profile: ProxyProfileEntity) {
        keepAliveJob?.cancel()
        val intervalMs = (profile.keepAliveSeconds.coerceIn(10, 120)) * 1000L
        keepAliveJob = serviceScope.launch {
            while (isActive) {
                delay(intervalMs)
                val probe = ProxyDialer.probeProxy(profile)
                VpnStateHolder.updatePing(probe.tcpLatencyMs)
                if (!probe.reachable) {
                    VpnStateHolder.log(LogLevel.WARN, "Keep-Alive sin respuesta (${probe.detail})")
                }
            }
        }
    }

    private fun stopTunnel(stopSelfAfter: Boolean) {
        telemetryTickerJob?.cancel()
        keepAliveJob?.cancel()
        engineJob?.cancel()
        packetEngine?.stop()
        packetEngine = null
        runCatching { tunInterface?.close() }
        tunInterface = null
        VpnStateHolder.updateState(
            VpnConnectionState.DISCONNECTED,
            "Túnel detenido"
        )
        VpnStateHolder.log(LogLevel.INFO, "VpnService desconectado")
        if (stopSelfAfter) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun registerNetworkMonitor() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        updateCurrentNetworkStatus(cm)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateCurrentNetworkStatus(cm)
                val isCell = VpnStateHolder.telemetry.value.isCellularCubacel
                if (lastCellularState != null && lastCellularState != isCell &&
                    VpnStateHolder.telemetry.value.state == VpnConnectionState.CONNECTED
                ) {
                    serviceScope.launch {
                        val settings = PreferencesRepository(applicationContext).getSnapshot()
                        if (settings.autoReconnectOnNetworkSwitch) {
                            VpnStateHolder.log(
                                LogLevel.INFO,
                                "Cambio de red detectado -> re-enlazando MTU y túnel..."
                            )
                            stopTunnel(stopSelfAfter = false)
                            startTunnelInternal()
                        }
                    }
                }
                lastCellularState = isCell
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                updateCurrentNetworkStatus(cm)
            }
        }
        networkCallback = callback
        runCatching {
            cm.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
        }
    }

    private fun unregisterNetworkMonitor() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        networkCallback = null
    }

    private fun updateCurrentNetworkStatus(cm: ConnectivityManager) {
        val active = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(active)
        if (caps == null) {
            VpnStateHolder.updateNetworkInfo("Sin conectividad", false)
            return
        }
        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isCell = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val label = when {
            isCell -> "Datos Móviles Cubacel (LTE/3G)"
            isWifi -> "Wi-Fi / Nauta"
            else -> "Red Activa"
        }
        VpnStateHolder.updateNetworkInfo(label, isCell)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.vpn_channel_description)
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(subtitle: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, CubacelVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.vpn_notif_connected))
            .setContentText(subtitle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.vpn_notif_disconnect), stopIntent)
            .build()
    }

    private fun formatRate(bps: Long): String {
        return when {
            bps < 1024 -> "${bps} B/s"
            bps < 1024 * 1024 -> String.format("%.1f KB/s", bps / 1024.0)
            else -> String.format("%.2f MB/s", bps / (1024.0 * 1024.0))
        }
    }
}
