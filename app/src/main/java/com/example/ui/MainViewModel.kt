package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.CubacelSettings
import com.example.data.InstalledAppsRepository
import com.example.data.PreferencesRepository
import com.example.data.ProxyProfileEntity
import com.example.data.ProxyUriParser
import com.example.model.AppRuleStatus
import com.example.model.CubacelPreset
import com.example.model.DiagnosticProbeResult
import com.example.model.InstalledAppInfo
import com.example.model.LogLevel
import com.example.model.ProxyType
import com.example.model.SplitTunnelMode
import com.example.model.VpnConnectionState
import com.example.model.VpnLogEntry
import com.example.model.VpnTelemetry
import com.example.vpn.CubacelVpnService
import com.example.vpn.ProxyDialer
import com.example.vpn.VpnStateHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppFilterCategory(val label: String) {
    ALL("Todas"),
    CUBAN("Cubanas"),
    TUNNEL("Por Túnel"),
    BYPASS("Directo"),
    BLOCKED("Bloqueadas")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val profileDao = db.proxyProfileDao()
    private val splitRuleDao = db.appSplitRuleDao()
    private val prefsRepo = PreferencesRepository(application)
    private val appsRepo = InstalledAppsRepository(application, splitRuleDao)

    val telemetry: StateFlow<VpnTelemetry> = VpnStateHolder.telemetry
    val logs: StateFlow<List<VpnLogEntry>> = VpnStateHolder.logs

    val profiles: StateFlow<List<ProxyProfileEntity>> = profileDao.observeAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProfile: StateFlow<ProxyProfileEntity?> = profileDao.observeActiveProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val settings: StateFlow<CubacelSettings> = prefsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CubacelSettings())

    private val _rawInstalledApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val _appSearchQuery = MutableStateFlow("")
    val appSearchQuery: StateFlow<String> = _appSearchQuery.asStateFlow()

    private val _appFilter = MutableStateFlow(AppFilterCategory.ALL)
    val appFilter: StateFlow<AppFilterCategory> = _appFilter.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(true)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    val filteredApps: StateFlow<List<InstalledAppInfo>> = combine(
        _rawInstalledApps,
        splitRuleDao.observeAllRules(),
        _appSearchQuery,
        _appFilter
    ) { apps, rules, query, filter ->
        val ruleMap = rules.associate { it.packageName to AppRuleStatus.fromName(it.ruleStatus) }
        val merged = apps.map { app ->
            val resolvedRule = ruleMap[app.packageName] ?: app.ruleStatus
            app.copy(ruleStatus = resolvedRule)
        }
        merged.filter { app ->
            val matchesQuery = query.isBlank() ||
                app.appName.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
            val matchesFilter = when (filter) {
                AppFilterCategory.ALL -> true
                AppFilterCategory.CUBAN -> app.isCubanApp
                AppFilterCategory.TUNNEL -> app.ruleStatus == AppRuleStatus.VPN_TUNNEL
                AppFilterCategory.BYPASS -> app.ruleStatus == AppRuleStatus.BYPASS_DIRECT
                AppFilterCategory.BLOCKED -> app.ruleStatus == AppRuleStatus.BLOCKED_NO_NET
            }
            matchesQuery && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAppsWithRules: StateFlow<List<InstalledAppInfo>> = combine(
        _rawInstalledApps,
        splitRuleDao.observeAllRules()
    ) { apps, rules ->
        val ruleMap = rules.associate { it.packageName to AppRuleStatus.fromName(it.ruleStatus) }
        apps.map { app -> app.copy(ruleStatus = ruleMap[app.packageName] ?: app.ruleStatus) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _probeResults = MutableStateFlow<List<DiagnosticProbeResult>>(emptyList())
    val probeResults: StateFlow<List<DiagnosticProbeResult>> = _probeResults.asStateFlow()

    private val _isProbing = MutableStateFlow(false)
    val isProbing: StateFlow<Boolean> = _isProbing.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            AppDatabase.ensureDefaultStarterProfiles(profileDao)
            refreshInstalledApps()
        }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            _rawInstalledApps.value = appsRepo.loadInstalledApps()
            _isLoadingApps.value = false
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun updateAppSearchQuery(query: String) {
        _appSearchQuery.value = query
    }

    fun updateAppFilter(category: AppFilterCategory) {
        _appFilter.value = category
    }

    fun selectActiveProfile(profileId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            profileDao.setActiveProfile(profileId)
            val chosen = profileDao.getActiveProfile()
            if (chosen != null) {
                VpnStateHolder.log(LogLevel.INFO, "Perfil seleccionado: ${chosen.name}")
                if (telemetry.value.state == VpnConnectionState.CONNECTED) {
                    CubacelVpnService.reloadVpn(getApplication())
                }
            }
        }
    }

    fun saveProfile(profile: ProxyProfileEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val insertedId = profileDao.insertProfile(profile)
            if (profile.isActive || profiles.value.isEmpty()) {
                val targetId = if (profile.id == 0L) insertedId else profile.id
                profileDao.setActiveProfile(targetId)
                if (telemetry.value.state == VpnConnectionState.CONNECTED) {
                    CubacelVpnService.reloadVpn(getApplication())
                }
            }
            _snackbarMessage.value = "Perfil '${profile.name}' guardado"
        }
    }

    fun deleteProfile(profile: ProxyProfileEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val all = profileDao.getAllProfilesSnapshot()
            if (all.size <= 1) {
                _snackbarMessage.value = "Debe quedar al menos un perfil configurado"
                return@launch
            }
            profileDao.deleteById(profile.id)
            if (profile.isActive) {
                val remaining = profileDao.getAllProfilesSnapshot().firstOrNull()
                remaining?.let { profileDao.setActiveProfile(it.id) }
            }
            _snackbarMessage.value = "Perfil eliminado"
        }
    }

    fun importProxyUri(rawUri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val parsed = ProxyUriParser.parseUri(rawUri)
            if (parsed == null) {
                _snackbarMessage.value = "Formato URI no reconocido (usa socks5://, http://, ss://, vless://, vmess://, trojan://, ssh://)"
                return@launch
            }
            val newId = profileDao.insertProfile(parsed)
            profileDao.setActiveProfile(newId)
            _snackbarMessage.value = "Importado y activado: ${parsed.name}"
            if (telemetry.value.state == VpnConnectionState.CONNECTED) {
                CubacelVpnService.reloadVpn(getApplication())
            }
        }
    }

    fun copyProfileUri(profile: ProxyProfileEntity) {
        val uri = ProxyUriParser.exportUri(profile)
        val cm = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("CubaTunnel URI", uri))
        _snackbarMessage.value = "URI copiada al portapapeles"
    }

    fun probeSingleProfile(profile: ProxyProfileEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            _snackbarMessage.value = "Probando latencia hacia ${profile.host}:${profile.port}..."
            val res = ProxyDialer.probeProxy(profile)
            profileDao.updateLatency(profile.id, res.tcpLatencyMs)
            _snackbarMessage.value = if (res.reachable) {
                "${profile.name}: ${res.detail}"
            } else {
                "Sin respuesta: ${res.detail}"
            }
        }
    }

    fun setAppRule(packageName: String, status: AppRuleStatus) {
        viewModelScope.launch(Dispatchers.IO) {
            appsRepo.setAppRule(packageName, status)
            if (telemetry.value.state == VpnConnectionState.CONNECTED) {
                CubacelVpnService.reloadVpn(getApplication())
            }
        }
    }

    fun applyCubanBypassPreset() {
        viewModelScope.launch(Dispatchers.IO) {
            prefsRepo.setSplitTunnelMode(SplitTunnelMode.EXCLUDE_SELECTED)
            appsRepo.applyCubanBypassPreset(_rawInstalledApps.value)
            _snackbarMessage.value = "Preset aplicado: apps cubanas (.cu) en Directo"
            if (telemetry.value.state == VpnConnectionState.CONNECTED) {
                CubacelVpnService.reloadVpn(getApplication())
            }
        }
    }

    fun applyMessagingOnlyPreset() {
        viewModelScope.launch(Dispatchers.IO) {
            prefsRepo.setSplitTunnelMode(SplitTunnelMode.INCLUDE_ONLY)
            appsRepo.applyMessagingOnlyWhitelistPreset(_rawInstalledApps.value)
            _snackbarMessage.value = "Preset aplicado: solo mensajería por túnel VPN"
            if (telemetry.value.state == VpnConnectionState.CONNECTED) {
                CubacelVpnService.reloadVpn(getApplication())
            }
        }
    }

    fun setSplitTunnelMode(mode: SplitTunnelMode) {
        viewModelScope.launch(Dispatchers.IO) {
            prefsRepo.setSplitTunnelMode(mode)
            if (telemetry.value.state == VpnConnectionState.CONNECTED) {
                CubacelVpnService.reloadVpn(getApplication())
            }
        }
    }

    fun applyCubacelPreset(preset: CubacelPreset) {
        viewModelScope.launch(Dispatchers.IO) {
            prefsRepo.setCubacelPreset(preset)
            val active = profileDao.getActiveProfile()
            if (active != null) {
                profileDao.updateProfile(
                    active.copy(
                        customMtu = preset.recommendedMtu,
                        keepAliveSeconds = preset.keepAliveSec,
                        isCubacelOptimized = true
                    )
                )
            }
            _snackbarMessage.value = "Optimización ${preset.displayName} aplicada (MTU ${preset.recommendedMtu})"
            if (telemetry.value.state == VpnConnectionState.CONNECTED) {
                CubacelVpnService.reloadVpn(getApplication())
            }
        }
    }

    fun setBypassCuRoutes(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            prefsRepo.setBypassCuRoutes(enabled)
            if (telemetry.value.state == VpnConnectionState.CONNECTED) {
                CubacelVpnService.reloadVpn(getApplication())
            }
        }
    }

    fun setBlockIpv6Leaks(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            prefsRepo.setBlockIpv6Leaks(enabled)
            if (telemetry.value.state == VpnConnectionState.CONNECTED) {
                CubacelVpnService.reloadVpn(getApplication())
            }
        }
    }

    fun setUseDohDns(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            prefsRepo.setUseDohDns(enabled)
            if (telemetry.value.state == VpnConnectionState.CONNECTED) {
                CubacelVpnService.reloadVpn(getApplication())
            }
        }
    }

    fun setDohProvider(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            prefsRepo.setDohProviderUrl(url)
            if (telemetry.value.state == VpnConnectionState.CONNECTED) {
                CubacelVpnService.reloadVpn(getApplication())
            }
        }
    }

    fun setAutoReconnect(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { prefsRepo.setAutoReconnectOnNetworkSwitch(enabled) }
    }

    fun setAutoFailover(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { prefsRepo.setAutoFailoverProxy(enabled) }
    }

    fun setAdaptiveMtu(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { prefsRepo.setAdaptiveMtu(enabled) }
    }

    fun setDailyQuotaMb(mb: Int) {
        viewModelScope.launch(Dispatchers.IO) { prefsRepo.setDailyQuotaMb(mb) }
    }

    fun setDarkTheme(dark: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { prefsRepo.setDarkTheme(dark) }
    }

    fun runNetworkDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            _isProbing.value = true
            val targets = mutableListOf<ProxyProfileEntity>()
            activeProfile.value?.let { targets.add(it) }
            targets.add(
                ProxyProfileEntity(
                    name = "Cloudflare DoH (1.1.1.1)",
                    proxyType = ProxyType.DOH_DIRECT.name,
                    host = "1.1.1.1",
                    port = 443,
                    dnsServer = "1.1.1.1"
                )
            )
            targets.add(
                ProxyProfileEntity(
                    name = "Google DoH (8.8.8.8)",
                    proxyType = ProxyType.DOH_DIRECT.name,
                    host = "8.8.8.8",
                    port = 443,
                    dnsServer = "8.8.8.8"
                )
            )
            val results = targets.map { ProxyDialer.probeProxy(it) }
            _probeResults.value = results
            _isProbing.value = false
        }
    }

    fun clearLogs() {
        VpnStateHolder.clearLogs()
    }
}
