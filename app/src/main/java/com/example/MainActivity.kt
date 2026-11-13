package com.example

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.model.VpnConnectionState
import com.example.ui.MainViewModel
import com.example.ui.screens.CubacelToolsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ProxiesScreen
import com.example.ui.screens.SplitTunnelScreen
import com.example.ui.theme.CubaTunnelTheme
import com.example.vpn.CubacelVpnService

object Routes {
    const val DASHBOARD = "dashboard"
    const val PROXIES = "proxies"
    const val SPLIT_TUNNEL = "split_tunnel"
    const val CUBACEL_TOOLS = "cubacel_tools"
}

private data class NavTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            CubaTunnelTheme(darkTheme = settings.darkTheme) {
                CubaTunnelApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CubaTunnelApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val filteredApps by viewModel.filteredApps.collectAsStateWithLifecycle()
    val allApps by viewModel.allAppsWithRules.collectAsStateWithLifecycle()
    val searchQuery by viewModel.appSearchQuery.collectAsStateWithLifecycle()
    val activeFilter by viewModel.appFilter.collectAsStateWithLifecycle()
    val isLoadingApps by viewModel.isLoadingApps.collectAsStateWithLifecycle()
    val probeResults by viewModel.probeResults.collectAsStateWithLifecycle()
    val isProbing by viewModel.isProbing.collectAsStateWithLifecycle()
    val snackbarMsg by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            CubacelVpnService.startVpn(context)
        } else {
            viewModel.showMessage("Permiso VPN denegado por el usuario")
        }
    }

    val launchVpnSafely: () -> Unit = {
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            CubacelVpnService.startVpn(context)
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        // Proceed regardless; notification is optional if user declines on Android 13+
        launchVpnSafely()
    }

    val onToggleVpn: () -> Unit = {
        val isActive = telemetry.state == VpnConnectionState.CONNECTED ||
            telemetry.state == VpnConnectionState.CONNECTING ||
            telemetry.state == VpnConnectionState.RECONNECTING
        if (isActive) {
            CubacelVpnService.stopVpn(context)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                launchVpnSafely()
            }
        }
    }

    val tabs = listOf(
        NavTab(Routes.DASHBOARD, R.string.nav_dashboard, Icons.Rounded.Radar, Icons.Outlined.Radar),
        NavTab(Routes.PROXIES, R.string.nav_proxies, Icons.Rounded.Hub, Icons.Outlined.Hub),
        NavTab(Routes.SPLIT_TUNNEL, R.string.nav_split_tunnel, Icons.Rounded.AltRoute, Icons.Outlined.AltRoute),
        NavTab(Routes.CUBACEL_TOOLS, R.string.nav_cubacel, Icons.Rounded.Tune, Icons.Outlined.Tune)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Routes.DASHBOARD

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_art),
                                contentDescription = "Logo CubaTunnel",
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = stringResource(id = R.string.app_name),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    },
                    actions = {
                        val isConnected = telemetry.state == VpnConnectionState.CONNECTED
                        val pillColor = if (isConnected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = pillColor.copy(alpha = 0.14f),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(pillColor)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isConnected) "VPN ON" else "VPN OFF",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = pillColor
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            val label = stringResource(id = tab.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            tabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                val label = stringResource(id = tab.labelRes)
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateToTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = label
                                        )
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("rail_${tab.route}")
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(
                        navController = navController,
                        startDestination = Routes.DASHBOARD
                    ) {
                        composable(Routes.DASHBOARD) {
                            DashboardScreen(
                                telemetry = telemetry,
                                activeProfile = activeProfile,
                                settings = settings,
                                logs = logs,
                                onToggleVpn = onToggleVpn,
                                onSelectPreset = viewModel::applyCubacelPreset,
                                onOpenProxies = { navigateToTab(Routes.PROXIES) },
                                onOpenSplitTunnel = { navigateToTab(Routes.SPLIT_TUNNEL) },
                                onProbeActive = viewModel::probeSingleProfile,
                                onClearLogs = viewModel::clearLogs
                            )
                        }
                        composable(Routes.PROXIES) {
                            ProxiesScreen(
                                profiles = profiles,
                                onSelectProfile = viewModel::selectActiveProfile,
                                onSaveProfile = viewModel::saveProfile,
                                onDeleteProfile = viewModel::deleteProfile,
                                onImportUri = viewModel::importProxyUri,
                                onCopyUri = viewModel::copyProfileUri,
                                onProbeProfile = viewModel::probeSingleProfile
                            )
                        }
                        composable(Routes.SPLIT_TUNNEL) {
                            SplitTunnelScreen(
                                apps = filteredApps,
                                allAppsCount = allApps.size,
                                splitMode = settings.splitTunnelMode,
                                searchQuery = searchQuery,
                                activeFilter = activeFilter,
                                isLoading = isLoadingApps,
                                onSplitModeChange = viewModel::setSplitTunnelMode,
                                onSearchQueryChange = viewModel::updateAppSearchQuery,
                                onFilterChange = viewModel::updateAppFilter,
                                onAppRuleChange = viewModel::setAppRule,
                                onApplyCubanPreset = viewModel::applyCubanBypassPreset,
                                onApplyMessagingPreset = viewModel::applyMessagingOnlyPreset,
                                onRefreshApps = viewModel::refreshInstalledApps
                            )
                        }
                        composable(Routes.CUBACEL_TOOLS) {
                            CubacelToolsScreen(
                                settings = settings,
                                probeResults = probeResults,
                                isProbing = isProbing,
                                onBypassCuChange = viewModel::setBypassCuRoutes,
                                onBlockIpv6Change = viewModel::setBlockIpv6Leaks,
                                onAdaptiveMtuChange = viewModel::setAdaptiveMtu,
                                onUseDohChange = viewModel::setUseDohDns,
                                onDohProviderChange = viewModel::setDohProvider,
                                onAutoReconnectChange = viewModel::setAutoReconnect,
                                onAutoFailoverChange = viewModel::setAutoFailover,
                                onDailyQuotaChange = viewModel::setDailyQuotaMb,
                                onDarkThemeChange = viewModel::setDarkTheme,
                                onRunDiagnostics = viewModel::runNetworkDiagnostics
                            )
                        }
                    }
                }
            }
        }
    }
}
