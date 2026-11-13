package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.CubacelSettings
import com.example.data.ProxyProfileEntity
import com.example.model.CubacelPreset
import com.example.model.LogLevel
import com.example.model.VpnConnectionState
import com.example.model.VpnLogEntry
import com.example.model.VpnTelemetry
import com.example.ui.theme.AmberCaution
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CyanSignal
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    telemetry: VpnTelemetry,
    activeProfile: ProxyProfileEntity?,
    settings: CubacelSettings,
    logs: List<VpnLogEntry>,
    onToggleVpn: () -> Unit,
    onSelectPreset: (CubacelPreset) -> Unit,
    onOpenProxies: () -> Unit,
    onOpenSplitTunnel: () -> Unit,
    onProbeActive: (ProxyProfileEntity) -> Unit,
    onClearLogs: () -> Unit
) {
    var showConsole by remember { mutableStateOf(true) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(telemetry.state, telemetry.connectedSinceMs) {
        while (telemetry.state == VpnConnectionState.CONNECTED && telemetry.connectedSinceMs > 0L) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val isConnected = telemetry.state == VpnConnectionState.CONNECTED
    val isConnecting = telemetry.state == VpnConnectionState.CONNECTING ||
        telemetry.state == VpnConnectionState.RECONNECTING

    val accentColor by animateColorAsState(
        targetValue = when (telemetry.state) {
            VpnConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
            VpnConnectionState.CONNECTING, VpnConnectionState.RECONNECTING -> MaterialTheme.colorScheme.secondary
            VpnConnectionState.ERROR -> MaterialTheme.colorScheme.error
            VpnConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(300),
        label = "accentColor"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Hero Connection & Radar Card
            item {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_tunnel_hero),
                            contentDescription = "Fondo de telemetría de red Cubacel",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .alpha(if (isConnected) 0.42f else 0.22f)
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                                        )
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Top status pill + network indicator
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = accentColor.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(accentColor)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = telemetry.state.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = accentColor
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (telemetry.isCellularCubacel) Icons.Rounded.CellTower else Icons.Rounded.Wifi,
                                            contentDescription = "Tipo de red actual",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = telemetry.networkTypeLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(22.dp))

                            // Animated Power Shield Button
                            VpnPowerButton(
                                isConnected = isConnected,
                                isConnecting = isConnecting,
                                accentColor = accentColor,
                                onClick = onToggleVpn
                            )

                            Spacer(Modifier.height(16.dp))

                            val elapsedSec = if (isConnected && telemetry.connectedSinceMs > 0L) {
                                ((nowMillis - telemetry.connectedSinceMs) / 1000L).coerceAtLeast(0L)
                            } else 0L

                            Text(
                                text = if (isConnected) formatDuration(elapsedSec) else "VPNSERVICE EN ESPERA",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(Modifier.height(4.dp))

                            Text(
                                text = telemetry.statusDetail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 2. Live Telemetry Grid (2x2)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelemetryStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Download,
                            label = "DESCARGA",
                            value = formatRate(telemetry.rxBytesPerSec),
                            subValue = "Total ${formatBytes(telemetry.rxBytes)}",
                            accent = EmeraldPulse
                        )
                        TelemetryStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Upload,
                            label = "SUBIDA",
                            value = formatRate(telemetry.txBytesPerSec),
                            subValue = "Total ${formatBytes(telemetry.txBytes)}",
                            accent = CyanSignal
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelemetryStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Speed,
                            label = "LATENCIA / MTU",
                            value = telemetry.lastPingMs?.let { "$it ms" } ?: "-- ms",
                            subValue = "MTU ${telemetry.activeMtu} B · ${telemetry.activeSessionsCount} TCP",
                            accent = AmberCaution
                        )
                        TelemetryStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Dns,
                            label = "DNS / CORTAFUEGOS",
                            value = "${telemetry.dnsQueriesCount} DNS",
                            subValue = "${telemetry.blockedPacketsCount} paq. bloqueados",
                            accent = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // 3. Cubacel Data Quota Guard Bar
            item {
                val totalSessionMb = (telemetry.rxBytes + telemetry.txBytes) / (1024.0 * 1024.0)
                val quotaMb = settings.dailyQuotaMb.coerceAtLeast(1)
                val progress = (totalSessionMb / quotaMb.toDouble()).toFloat().coerceIn(0f, 1f)
                val barColor = when {
                    progress > 0.85f -> CoralAlert
                    progress > 0.65f -> AmberCaution
                    else -> MaterialTheme.colorScheme.primary
                }

                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Shield,
                                    contentDescription = null,
                                    tint = barColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Control de Paquete Cubacel",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Text(
                                text = String.format("%.2f / %d MB", totalSessionMb, quotaMb),
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 12.sp,
                                color = barColor
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            color = barColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }

            // 4. Active Profile Card
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProxies() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = activeProfile?.resolvedProxyType()?.shortBadge ?: "N/A",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Perfil Activo",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = activeProfile?.name ?: "Selecciona un perfil proxy",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (activeProfile != null) {
                                    "${activeProfile.host}:${activeProfile.port} · Split: ${settings.splitTunnelMode.displayName}"
                                } else "Toca para configurar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (activeProfile != null) {
                                IconButton(
                                    onClick = { onProbeActive(activeProfile) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("probe_active_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bolt,
                                        contentDescription = "Probar latencia del proxy activo",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = "Ir a Proxies",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 5. Cubacel Preset Quick Selector + Split Tunnel Shortcut
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Optimización Cubacel (MTU / Keep-Alive)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        TextButton(
                            onClick = onOpenSplitTunnel,
                            modifier = Modifier.testTag("dashboard_split_shortcut")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Split Túnel")
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(CubacelPreset.entries) { preset ->
                            val selected = settings.cubacelPreset == preset
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectPreset(preset) },
                                label = {
                                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                        Text(
                                            text = preset.displayName,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Text(
                                            text = "${preset.badge} · ${preset.keepAliveSec}s",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 10.sp
                                        )
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("preset_${preset.name}")
                            )
                        }
                    }
                }
            }

            // 6. Live VpnService Telemetry Console
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showConsole = !showConsole }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Terminal,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Consola VpnService (${logs.size})",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            IconButton(
                                onClick = onClearLogs,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteSweep,
                                    contentDescription = "Limpiar registros",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        AnimatedVisibility(visible = showConsole) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (logs.isEmpty()) {
                                    Text(
                                        text = "Sin eventos aún. Pulsa el botón de encendido para iniciar el túnel VpnService.",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    logs.take(12).forEach { entry ->
                                        val lvlColor = when (entry.level) {
                                            LogLevel.SUCCESS -> EmeraldPulse
                                            LogLevel.WARN -> AmberCaution
                                            LogLevel.ERROR -> CoralAlert
                                            LogLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        Text(
                                            text = "[${entry.timestamp}] ${entry.message}",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            color = lvlColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VpnPowerButton(
    isConnected: Boolean,
    isConnecting: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isConnected || isConnecting) 1.15f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(144.dp)
    ) {
        // Outer glowing pulse halo
        Box(
            modifier = Modifier
                .size(134.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = if (isConnected) 0.18f else 0.08f))
        )
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (isConnected) accentColor else MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(2.5.dp, accentColor),
            shadowElevation = if (isConnected) 12.dp else 4.dp,
            modifier = Modifier
                .size(106.dp)
                .testTag("vpn_power_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = if (isConnected) "Desconectar VPN" else "Conectar VPN",
                    tint = if (isConnected) MaterialTheme.colorScheme.onPrimary else accentColor,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
    }
}

@Composable
private fun TelemetryStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    subValue: String,
    accent: Color
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subValue,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatDuration(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}

private fun formatRate(bps: Long): String {
    return when {
        bps < 1024 -> "$bps B/s"
        bps < 1024 * 1024 -> String.format("%.1f KB/s", bps / 1024.0)
        else -> String.format("%.2f MB/s", bps / (1024.0 * 1024.0))
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
