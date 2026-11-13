package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ProxyProfileEntity
import com.example.model.ProxyType
import com.example.ui.theme.AmberCaution
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.JetBrainsMonoFamily

private enum class ProtocolFilter(val label: String) {
    ALL("Todos"),
    SOCKS("SOCKS4/5"),
    HTTP("HTTP/Payload"),
    TLS_CDN("TLS/WS/CDN"),
    SSH("SSH"),
    XRAY_SS("SS/VLESS/VMess/Trojan"),
    DNS("DNS/SlowDNS")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxiesScreen(
    profiles: List<ProxyProfileEntity>,
    onSelectProfile: (Long) -> Unit,
    onSaveProfile: (ProxyProfileEntity) -> Unit,
    onDeleteProfile: (ProxyProfileEntity) -> Unit,
    onImportUri: (String) -> Unit,
    onCopyUri: (ProxyProfileEntity) -> Unit,
    onProbeProfile: (ProxyProfileEntity) -> Unit
) {
    var selectedFilter by remember { mutableStateOf(ProtocolFilter.ALL) }
    var editingProfile by remember { mutableStateOf<ProxyProfileEntity?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var uriInput by remember { mutableStateOf("") }

    val visibleProfiles = remember(profiles, selectedFilter) {
        profiles.filter { profile ->
            val type = profile.resolvedProxyType()
            when (selectedFilter) {
                ProtocolFilter.ALL -> true
                ProtocolFilter.SOCKS -> type == ProxyType.SOCKS5 || type == ProxyType.SOCKS4
                ProtocolFilter.HTTP -> type == ProxyType.HTTP_CONNECT || type == ProxyType.HTTP_PAYLOAD
                ProtocolFilter.TLS_CDN -> type == ProxyType.SSL_TLS_SNI || type == ProxyType.WS_TLS_CDN
                ProtocolFilter.SSH -> type == ProxyType.SSH_TUNNEL
                ProtocolFilter.XRAY_SS -> type in listOf(
                    ProxyType.SHADOWSOCKS, ProxyType.VLESS, ProxyType.VMESS, ProxyType.TROJAN
                )
                ProtocolFilter.DNS -> type == ProxyType.SLOW_DNS || type == ProxyType.DOH_DIRECT
            }
        }
    }

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
                            text = "Tipos de Proxy y Túneles",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "13 protocolos configurables para Cubacel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showImportDialog = true },
                            modifier = Modifier.testTag("import_uri_button")
                        ) {
                            Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("URI")
                        }
                        Button(
                            onClick = {
                                editingProfile = ProxyProfileEntity(
                                    name = "Nuevo Proxy Cubacel",
                                    proxyType = ProxyType.SOCKS5.name,
                                    host = "",
                                    port = 1080
                                )
                            },
                            modifier = Modifier.testTag("add_proxy_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Crear")
                        }
                    }
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ProtocolFilter.entries) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label) }
                        )
                    }
                }
            }

            if (visibleProfiles.isEmpty()) {
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
                            Text(
                                text = "No hay perfiles en esta categoría",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Pulsa 'Crear' para configurar un proxy SOCKS5, HTTP Payload, TLS/SNI, SSH, Shadowsocks, VLESS, VMess, Trojan o SlowDNS.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(visibleProfiles, key = { it.id }) { profile ->
                    ProxyProfileCard(
                        profile = profile,
                        onSelect = { onSelectProfile(profile.id) },
                        onProbe = { onProbeProfile(profile) },
                        onCopyUri = { onCopyUri(profile) },
                        onEdit = { editingProfile = profile },
                        onDelete = { onDeleteProfile(profile) }
                    )
                }
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Importar enlace Proxy / VPN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Pega una URI socks5://, socks4://, http://, https://, ss://, vless://, vmess://, trojan://, ssh:// o ip:puerto",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = uriInput,
                        onValueChange = { uriInput = it },
                        label = { Text("URI de configuración") },
                        placeholder = { Text("socks5://user:pass@host:1080#MiProxy") },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_uri_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onImportUri(uriInput)
                        uriInput = ""
                        showImportDialog = false
                    },
                    modifier = Modifier.testTag("confirm_import_uri")
                ) {
                    Text("Importar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    editingProfile?.let { draft ->
        ModalBottomSheet(
            onDismissRequest = { editingProfile = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            ProxyProfileEditorSheet(
                initial = draft,
                onCancel = { editingProfile = null },
                onSave = { updated ->
                    onSaveProfile(updated)
                    editingProfile = null
                }
            )
        }
    }
}

@Composable
private fun ProxyProfileCard(
    profile: ProxyProfileEntity,
    onSelect: () -> Unit,
    onProbe: () -> Unit,
    onCopyUri: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val type = profile.resolvedProxyType()
    val borderColor = if (profile.isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (profile.isActive) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("proxy_card_${profile.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (profile.isActive) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = if (profile.isActive) "Perfil activo" else "Seleccionar perfil",
                        tint = if (profile.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (type.requiresRemoteServer) {
                                "${profile.host.ifBlank { "(sin host)" }}:${profile.port}"
                            } else {
                                "DNS local DoH (${profile.dnsServer})"
                            },
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = type.shortBadge,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    profile.lastLatencyMs?.let { ms ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${ms} ms",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = if (ms < 180) EmeraldPulse else AmberCaution
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = type.cubacelTip,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MTU ${profile.customMtu} · KA ${profile.keepAliveSeconds}s",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row {
                    IconButton(
                        onClick = onProbe,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = "Probar latencia",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    IconButton(
                        onClick = onCopyUri,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copiar URI"
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Editar perfil"
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Eliminar perfil",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProxyProfileEditorSheet(
    initial: ProxyProfileEntity,
    onCancel: () -> Unit,
    onSave: (ProxyProfileEntity) -> Unit
) {
    var selectedType by remember { mutableStateOf(initial.resolvedProxyType()) }
    var name by remember { mutableStateOf(initial.name) }
    var host by remember { mutableStateOf(initial.host) }
    var portText by remember { mutableStateOf(initial.port.toString()) }
    var username by remember { mutableStateOf(initial.username) }
    var password by remember { mutableStateOf(initial.password) }
    var sniHost by remember { mutableStateOf(initial.sniHost) }
    var httpPayload by remember { mutableStateOf(initial.httpPayload) }
    var wsPath by remember { mutableStateOf(initial.wsPath) }
    var methodOrFlow by remember { mutableStateOf(initial.encryptionMethod) }
    var uuidOrKey by remember { mutableStateOf(initial.uuidOrKey) }
    var dnsServer by remember { mutableStateOf(initial.dnsServer) }
    var mtuText by remember { mutableStateOf(initial.customMtu.toString()) }
    var keepAliveText by remember { mutableStateOf(initial.keepAliveSeconds.toString()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (initial.id == 0L) "Nuevo Perfil Proxy" else "Editar Perfil Proxy",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "Selecciona el protocolo:",
            style = MaterialTheme.typography.labelLarge
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ProxyType.entries) { pType ->
                FilterChip(
                    selected = selectedType == pType,
                    onClick = {
                        selectedType = pType
                        if (initial.id == 0L) {
                            portText = pType.defaultPort.toString()
                        }
                    },
                    label = { Text(pType.shortBadge) }
                )
            }
        }

        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = selectedType.displayName,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "${selectedType.description}\nTip Cubacel: ${selectedType.cubacelTip}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nombre del perfil") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (selectedType.requiresRemoteServer) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Servidor / IP / Host") },
                    singleLine = true,
                    modifier = Modifier.weight(2f)
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter(Char::isDigit) },
                    label = { Text("Puerto") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (selectedType.supportsAuth) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Usuario (opcional)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (selectedType.supportsSni) {
            OutlinedTextField(
                value = sniHost,
                onValueChange = { sniHost = it },
                label = { Text("SNI / Bug Host (ej. cdn.ejemplo.com)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (selectedType.supportsMethodOrFlow) {
            OutlinedTextField(
                value = uuidOrKey,
                onValueChange = { uuidOrKey = it },
                label = {
                    Text(
                        if (selectedType == ProxyType.SHADOWSOCKS) "Clave Shadowsocks" else "UUID / Clave"
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = methodOrFlow,
                    onValueChange = { methodOrFlow = it },
                    label = { Text("Cifrado / Transporte (ws, grpc, chacha20)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = wsPath,
                    onValueChange = { wsPath = it },
                    label = { Text("Ruta WS (/ws)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (selectedType.supportsPayload) {
            OutlinedTextField(
                value = httpPayload,
                onValueChange = { httpPayload = it },
                label = { Text("Payload HTTP / Inyector") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val macros = listOf("[host_port]", "[sni]", "[crlf]", "Connection: Keep-Alive")
                items(macros) { macro ->
                    FilledTonalButton(
                        onClick = { httpPayload += macro },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(macro, fontFamily = JetBrainsMonoFamily, fontSize = 11.sp)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = dnsServer,
                onValueChange = { dnsServer = it },
                label = { Text("DNS") },
                singleLine = true,
                modifier = Modifier.weight(1.2f)
            )
            OutlinedTextField(
                value = mtuText,
                onValueChange = { mtuText = it.filter(Char::isDigit) },
                label = { Text("MTU") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(0.9f)
            )
            OutlinedTextField(
                value = keepAliveText,
                onValueChange = { keepAliveText = it.filter(Char::isDigit) },
                label = { Text("KA (s)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(0.9f)
            )
        }

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancelar")
            }
            Button(
                onClick = {
                    val safePort = (portText.toIntOrNull() ?: selectedType.defaultPort).coerceIn(1, 65535)
                    val safeMtu = (mtuText.toIntOrNull() ?: 1360).coerceIn(1000, 1500)
                    val safeKa = (keepAliveText.toIntOrNull() ?: 25).coerceIn(10, 120)
                    onSave(
                        initial.copy(
                            name = name.ifBlank { selectedType.displayName },
                            proxyType = selectedType.name,
                            host = host.trim(),
                            port = safePort,
                            username = username.trim(),
                            password = password,
                            sniHost = sniHost.trim(),
                            httpPayload = httpPayload,
                            wsPath = wsPath.trim().ifBlank { "/ws" },
                            encryptionMethod = methodOrFlow.trim(),
                            uuidOrKey = uuidOrKey.trim(),
                            dnsServer = dnsServer.trim().ifBlank { "1.1.1.1" },
                            customMtu = safeMtu,
                            keepAliveSeconds = safeKa,
                            isActive = true
                        )
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("save_proxy_button")
            ) {
                Text("Guardar y Activar")
            }
        }
    }
}
