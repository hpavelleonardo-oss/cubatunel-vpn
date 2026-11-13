package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.CubacelPreset
import com.example.model.SplitTunnelMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.cubacelDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "cubatunnel_settings"
)

data class CubacelSettings(
    val splitTunnelMode: SplitTunnelMode = SplitTunnelMode.EXCLUDE_SELECTED,
    val cubacelPreset: CubacelPreset = CubacelPreset.CUBACEL_4G_LTE,
    val bypassCuRoutes: Boolean = true,
    val blockIpv6Leaks: Boolean = true,
    val useDohDns: Boolean = true,
    val dohProviderUrl: String = "https://cloudflare-dns.com/dns-query",
    val autoReconnectOnNetworkSwitch: Boolean = true,
    val autoFailoverProxy: Boolean = true,
    val adaptiveMtu: Boolean = true,
    val dailyQuotaMb: Int = 500,
    val darkTheme: Boolean = true
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val SPLIT_MODE = stringPreferencesKey("split_tunnel_mode")
        val CUBACEL_PRESET = stringPreferencesKey("cubacel_preset")
        val BYPASS_CU = booleanPreferencesKey("bypass_cu_routes")
        val BLOCK_IPV6 = booleanPreferencesKey("block_ipv6")
        val USE_DOH = booleanPreferencesKey("use_doh_dns")
        val DOH_PROVIDER = stringPreferencesKey("doh_provider_url")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect_network")
        val AUTO_FAILOVER = booleanPreferencesKey("auto_failover_proxy")
        val ADAPTIVE_MTU = booleanPreferencesKey("adaptive_mtu")
        val DAILY_QUOTA_MB = intPreferencesKey("daily_quota_mb")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
    }

    val settingsFlow: Flow<CubacelSettings> = context.cubacelDataStore.data.map { prefs ->
        CubacelSettings(
            splitTunnelMode = SplitTunnelMode.fromName(
                prefs[Keys.SPLIT_MODE] ?: SplitTunnelMode.EXCLUDE_SELECTED.name
            ),
            cubacelPreset = CubacelPreset.fromName(
                prefs[Keys.CUBACEL_PRESET] ?: CubacelPreset.CUBACEL_4G_LTE.name
            ),
            bypassCuRoutes = prefs[Keys.BYPASS_CU] ?: true,
            blockIpv6Leaks = prefs[Keys.BLOCK_IPV6] ?: true,
            useDohDns = prefs[Keys.USE_DOH] ?: true,
            dohProviderUrl = prefs[Keys.DOH_PROVIDER] ?: "https://cloudflare-dns.com/dns-query",
            autoReconnectOnNetworkSwitch = prefs[Keys.AUTO_RECONNECT] ?: true,
            autoFailoverProxy = prefs[Keys.AUTO_FAILOVER] ?: true,
            adaptiveMtu = prefs[Keys.ADAPTIVE_MTU] ?: true,
            dailyQuotaMb = prefs[Keys.DAILY_QUOTA_MB] ?: 500,
            darkTheme = prefs[Keys.DARK_THEME] ?: true
        )
    }

    suspend fun getSnapshot(): CubacelSettings = settingsFlow.first()

    suspend fun setSplitTunnelMode(mode: SplitTunnelMode) {
        context.cubacelDataStore.edit { it[Keys.SPLIT_MODE] = mode.name }
    }

    suspend fun setCubacelPreset(preset: CubacelPreset) {
        context.cubacelDataStore.edit { it[Keys.CUBACEL_PRESET] = preset.name }
    }

    suspend fun setBypassCuRoutes(enabled: Boolean) {
        context.cubacelDataStore.edit { it[Keys.BYPASS_CU] = enabled }
    }

    suspend fun setBlockIpv6Leaks(enabled: Boolean) {
        context.cubacelDataStore.edit { it[Keys.BLOCK_IPV6] = enabled }
    }

    suspend fun setUseDohDns(enabled: Boolean) {
        context.cubacelDataStore.edit { it[Keys.USE_DOH] = enabled }
    }

    suspend fun setDohProviderUrl(url: String) {
        context.cubacelDataStore.edit { it[Keys.DOH_PROVIDER] = url }
    }

    suspend fun setAutoReconnectOnNetworkSwitch(enabled: Boolean) {
        context.cubacelDataStore.edit { it[Keys.AUTO_RECONNECT] = enabled }
    }

    suspend fun setAutoFailoverProxy(enabled: Boolean) {
        context.cubacelDataStore.edit { it[Keys.AUTO_FAILOVER] = enabled }
    }

    suspend fun setAdaptiveMtu(enabled: Boolean) {
        context.cubacelDataStore.edit { it[Keys.ADAPTIVE_MTU] = enabled }
    }

    suspend fun setDailyQuotaMb(mb: Int) {
        context.cubacelDataStore.edit { it[Keys.DAILY_QUOTA_MB] = mb.coerceIn(50, 20000) }
    }

    suspend fun setDarkTheme(dark: Boolean) {
        context.cubacelDataStore.edit { it[Keys.DARK_THEME] = dark }
    }
}
