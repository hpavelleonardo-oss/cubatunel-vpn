package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CubacelSettings
import com.example.model.DiagnosticProbeResult
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.JetBrainsMonoFamily
import kotlin.math.roundToInt

@Composable
fun CubacelToolsScreen(
    settings: CubacelSettings,
    probeResults: List<DiagnosticProbeResult>,
    isProbing: Boolean,
    onBypassCuChange: (Boolean) -> Unit,
    onBlockIpv6Change: (Boolean) -> Unit,
    onAdaptiveMtuChange: (Boolean) -> Unit,
    onUseDohChange: (Boolean) -> Unit,
    onDohProviderChange: (String) -> Unit,
    onAutoReconnectChange: (Boolean) -> Unit,
    onAutoFailoverChange: (Boolean) -> Unit,
    onDailyQuotaChange: (Int) -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    onRunDiagnostics: () -> Unit
) {
    val dohProviders = listOf(
        "Cloudflare (1.1.1.1)" to "https://cloudflare-dns.com/dns-query",
        "Google (8.8.8.8)" to "https://dns.google/dns-query",
        "Quad9 (9.9.9.9)" to "https://dns.quad9.net/dns-query"
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
            item {
                Column {
                    Text(
                        text = "Motor Cubacel+ y Mejoras",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Ajustes de red para ETECSA Cuba, diagnóstico TCP/TLS y ahorro de datos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 1. Network Diagnostics Card
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.NetworkCheck,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Diagnóstico TCP / TLS en Vivo",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }

                        Text(
                            text = "Comprueba en tiempo real si tu perfil proxy activo y los servidores DoH responden desde tu red Cubacel actual.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = onRunDiagnostics,
                            enabled = !isProbing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("run_diagnostics_button")
                        ) {
                            if (isProbing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Midiendo latencia TCP/TLS...")
                            } else {
                                Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Ejecutar Diagnóstico Completo")
                            }
                        }

                        if (probeResults.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                probeResults.forEach { res ->
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (res.reachable) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                                contentDescription = null,
                                                tint = if (res.reachable) EmeraldPulse else CoralAlert,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${res.protocol} · ${res.target}",
                                                    fontFamily = JetBrainsMonoFamily,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = res.detail,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

            // 2. Cubacel Network Optimizations
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Optimizaciones para ETECSA / Cubacel",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(10.dp))

                        SettingSwitchRow(
                            icon = Icons.Rounded.CellTower,
                            title = "Bypass Red Nacional (.cu / ETECSA)",
                            subtitle = "Excluye rangos IP cubanos (Android 13+) y apps nacionales del túnel.",
                            checked = settings.bypassCuRoutes,
                            onCheckedChange = onBypassCuChange
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        SettingSwitchRow(
                            icon = Icons.Rounded.Security,
                            title = "Bloqueo Anti-Fugas IPv6",
                            subtitle = "Evita paquetes IPv6 sin túnel que consumen saldo o exponen DNS.",
                            checked = settings.blockIpv6Leaks,
                            onCheckedChange = onBlockIpv6Change
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        SettingSwitchRow(
                            icon = Icons.Rounded.AutoAwesome,
                            title = "MTU Adaptativo (3G ↔ 4G ↔ Wi-Fi)",
                            subtitle = "Ajusta automáticamente a 1280B en 3G, 1360B en 4G LTE y 1420B en Wi-Fi.",
                            checked = settings.adaptiveMtu,
                            onCheckedChange = onAdaptiveMtuChange
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        SettingSwitchRow(
                            icon = Icons.Rounded.Dns,
                            title = "DNS Cifrado sobre HTTPS (DoH)",
                            subtitle = "Resuelve consultas DNS del puerto 53 vía HTTPS protegido contra bloqueos.",
                            checked = settings.useDohDns,
                            onCheckedChange = onUseDohChange
                        )

                        if (settings.useDohDns) {
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(dohProviders) { (label, url) ->
                                    FilterChip(
                                        selected = settings.dohProviderUrl == url,
                                        onClick = { onDohProviderChange(url) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Service Resilience & Quota Controls
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Resiliencia y Ahorro de Datos",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(10.dp))

                        SettingSwitchRow(
                            icon = Icons.Rounded.SwapHoriz,
                            title = "Auto-Failover entre Proxies",
                            subtitle = "Si el servidor activo no responde en Cubacel, conmuta al siguiente perfil sano.",
                            checked = settings.autoFailoverProxy,
                            onCheckedChange = onAutoFailoverChange
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        SettingSwitchRow(
                            icon = Icons.Rounded.NetworkCheck,
                            title = "Re-enlace al Cambiar de Red",
                            subtitle = "Reinicia el túnel limpio al pasar de Wi-Fi Nauta a Datos Móviles Cubacel.",
                            checked = settings.autoReconnectOnNetworkSwitch,
                            onCheckedChange = onAutoReconnectChange
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Alerta de Cuota por Sesión",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "${settings.dailyQuotaMb} MB",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = settings.dailyQuotaMb.toFloat(),
                            onValueChange = { onDailyQuotaChange((it / 50f).roundToInt() * 50) },
                            valueRange = 100f..5000f
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        SettingSwitchRow(
                            icon = Icons.Rounded.DarkMode,
                            title = "Modo Nocturno de Telemetría",
                            subtitle = "Alterna entre el esquema oscuro de alto contraste y tema claro.",
                            checked = settings.darkTheme,
                            onCheckedChange = onDarkThemeChange
                        )
                    }
                }
            }

            // 4. Suggested Infrastructure & UX Improvements Card
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.TipsAndUpdates,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Recomendaciones Clave para Mejorar en Cuba",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        SuggestionBullet(
                            title = "1. Servidor VPS con TCP BBR + WebSocket TLS (443)",
                            detail = "Activar net.ipv4.tcp_congestion_control=bbr en tu VPS reduce drásticamente el buffering en horas pico de Cubacel."
                        )
                        SuggestionBullet(
                            title = "2. Cortafuegos 'Sin Red' en Apps en Segundo Plano",
                            detail = "Usa Split Túnel para bloquear actualizaciones y telemetría pesada y ahorrar megas de tu paquete LTE."
                        )
                        SuggestionBullet(
                            title = "3. BadVPN UDPGW para Llamadas VoIP",
                            detail = "Si usas SSH o SOCKS5 para llamadas de WhatsApp/Telegram, habilita UDPGW en el puerto 7300 del servidor."
                        )
                        SuggestionBullet(
                            title = "4. Auto-Failover Multi-Región",
                            detail = "Guarda al menos 2 perfiles (ej. Miami/CDN + Europa) con Auto-Failover activo para conmutar si uno se satura."
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SuggestionBullet(title: String, detail: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
