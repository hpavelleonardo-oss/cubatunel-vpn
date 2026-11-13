package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.model.AppRuleStatus
import com.example.model.InstalledAppInfo
import com.example.model.SplitTunnelMode
import com.example.ui.AppFilterCategory
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CyanSignal
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun SplitTunnelScreen(
    apps: List<InstalledAppInfo>,
    allAppsCount: Int,
    splitMode: SplitTunnelMode,
    searchQuery: String,
    activeFilter: AppFilterCategory,
    isLoading: Boolean,
    onSplitModeChange: (SplitTunnelMode) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (AppFilterCategory) -> Unit,
    onAppRuleChange: (String, AppRuleStatus) -> Unit,
    onApplyCubanPreset: () -> Unit,
    onApplyMessagingPreset: () -> Unit,
    onRefreshApps: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Split Túnel y Cortafuegos",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "$allAppsCount apps detectadas · Controla túnel, directo o bloqueo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onRefreshApps,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Actualizar lista de aplicaciones"
                        )
                    }
                }
            }

            // Mode selector
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Modo de Enrutamiento VpnService",
                            style = MaterialTheme.typography.titleSmall
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            val entries = SplitTunnelMode.entries
                            entries.forEachIndexed { index, mode ->
                                val shortName = when (mode) {
                                    SplitTunnelMode.ALL_APPS -> "Global"
                                    SplitTunnelMode.EXCLUDE_SELECTED -> "Excluir"
                                    SplitTunnelMode.INCLUDE_ONLY -> "Solo Elegidas"
                                }
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index, entries.size),
                                    selected = splitMode == mode,
                                    onClick = { onSplitModeChange(mode) },
                                    label = { Text(shortName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.testTag("split_mode_${mode.name}")
                                )
                            }
                        }
                        Text(
                            text = splitMode.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Quick Cubacel Presets
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onApplyCubanPreset,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("preset_bypass_cuba")
                    ) {
                        Icon(Icons.Rounded.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Bypass Apps .CU", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FilledTonalButton(
                        onClick = onApplyMessagingPreset,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("preset_messaging_only")
                    ) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Solo Mensajería", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Search bar & Filter chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Limpiar búsqueda")
                                }
                            }
                        },
                        placeholder = { Text("Buscar app (WhatsApp, Transfermóvil, Chrome...)") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("split_search_input")
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AppFilterCategory.entries) { cat ->
                            FilterChip(
                                selected = activeFilter == cat,
                                onClick = { onFilterChange(cat) },
                                label = { Text(cat.label) }
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (apps.isEmpty()) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Apps,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "No se encontraron aplicaciones con ese filtro",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }
            } else {
                items(apps, key = { it.packageName }) { app ->
                    AppSplitRuleRow(
                        app = app,
                        onStatusChange = { newStatus -> onAppRuleChange(app.packageName, newStatus) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppSplitRuleRow(
    app: InstalledAppInfo,
    onStatusChange: (AppRuleStatus) -> Unit
) {
    val iconBitmap = remember(app.packageName) {
        runCatching {
            app.icon?.toBitmap(width = 88, height = 88)?.asImageBitmap()
        }.getOrNull()
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_rule_${app.packageName}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = app.appName,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Apps, contentDescription = app.appName)
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (app.isCubanApp) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = ".CU",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = app.packageName,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RuleChoicePill(
                    modifier = Modifier.weight(1f),
                    label = "Túnel",
                    selected = app.ruleStatus == AppRuleStatus.VPN_TUNNEL,
                    activeColor = EmeraldPulse,
                    icon = Icons.Rounded.VpnLock,
                    onClick = { onStatusChange(AppRuleStatus.VPN_TUNNEL) }
                )
                RuleChoicePill(
                    modifier = Modifier.weight(1f),
                    label = "Directo",
                    selected = app.ruleStatus == AppRuleStatus.BYPASS_DIRECT,
                    activeColor = CyanSignal,
                    icon = Icons.Rounded.Public,
                    onClick = { onStatusChange(AppRuleStatus.BYPASS_DIRECT) }
                )
                RuleChoicePill(
                    modifier = Modifier.weight(1f),
                    label = "Sin Red",
                    selected = app.ruleStatus == AppRuleStatus.BLOCKED_NO_NET,
                    activeColor = CoralAlert,
                    icon = Icons.Rounded.Block,
                    onClick = { onStatusChange(AppRuleStatus.BLOCKED_NO_NET) }
                )
            }
        }
    }
}

@Composable
private fun RuleChoicePill(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    activeColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) activeColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) activeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        modifier = modifier.height(38.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (selected) Icons.Rounded.Check else icon,
                contentDescription = null,
                tint = if (selected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
