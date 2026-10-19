package dev.j3pf.rp.ui.settings

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.j3pf.rp.BuildConfig
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.repo.NotifyCategory
import dev.j3pf.rp.protocol.DeviceInfo
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.ui.components.AppHeader
import dev.j3pf.rp.ui.components.ColorDot
import dev.j3pf.rp.ui.components.SectionTitle
import dev.j3pf.rp.ui.components.SurfaceCard
import dev.j3pf.rp.ui.theme.RpColors
import dev.j3pf.rp.ui.theme.RpThemeChoice
import dev.j3pf.rp.util.agentTitle
import dev.j3pf.rp.util.contextEmoji
import dev.j3pf.rp.util.contextLabel
import dev.j3pf.rp.util.parseColor
import dev.j3pf.rp.util.relativeTime

/** Ajustes: relay, dispositivos y máquinas, notificaciones, seguridad, batería, versión/licencias y cerrar sesión. */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onNav: (SettingsDest) -> Unit,
    onSelectTheme: (String) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectFontScale: (Float) -> Unit = {},
) {
    var confirmLogout by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showFontScaleDialog by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 120.dp),
    ) {
        AppHeader(
            greeting = "Configuración y estado",
            title = "Ajustes",
        )

        SectionTitle("Relay")
        SurfaceCard(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 1.dp,
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        state.session?.relayName?.ifBlank { null } ?: "Relay",
                        fontWeight = FontWeight.Bold,
                    )
                },
                supportingContent = { Text(state.session?.relayUrl.orEmpty(), style = MaterialTheme.typography.bodySmall) },
                trailingContent = {
                    val (statusText, statusColor) =
                        when (state.conn) {
                            ConnState.Connected -> "● conectado" to RpColors.success
                            ConnState.Connecting -> "conectando…" to MaterialTheme.colorScheme.primary
                            is ConnState.Fatal -> "✕ cerrado" to MaterialTheme.colorScheme.error
                            else -> "○ sin conexión" to MaterialTheme.colorScheme.outline
                        }
                    Text(
                        statusText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = statusColor,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        SectionTitle("Equipos")
        SurfaceCard(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 1.dp,
        ) {
            NavRow("Dispositivos emparejados", "Revocar teléfonos", icon = Icons.Filled.Smartphone) { onNav(SettingsDest.Devices) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            NavRow("PCs / máquinas", "Nombre, contexto, color; revocar", icon = Icons.Filled.Computer) { onNav(SettingsDest.Machines) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            NavRow("Añadir máquina", "Genera un código para `/rp pair`", icon = Icons.Filled.Add) { onNav(SettingsDest.AddMachine) }
        }

        SectionTitle("Preferencias")
        SurfaceCard(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 1.dp,
        ) {
            NavRow("Tema de la app", themeLabel(state.settings.appTheme), icon = Icons.Filled.Palette) { showThemeDialog = true }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            NavRow(
                "Tamaño de letra en el chat",
                fontScaleLabel(state.settings.chatFontScale),
                icon = Icons.Filled.Palette,
            ) { showFontScaleDialog = true }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            NavRow("Notificaciones", healthSummary(state.health), icon = Icons.Filled.Notifications) { onNav(SettingsDest.Notifications) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            NavRow("Seguridad", "Desbloqueo para aprobar, bloqueo de la app", icon = Icons.Filled.Security) { onNav(SettingsDest.Security) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            NavRow(
                "Optimización de batería",
                if (state.health.batteryExcluded) "RP está excluida ✓" else "Recomendado excluir RP",
                icon = Icons.Filled.BatteryChargingFull,
            ) {
                onNav(SettingsDest.Battery)
            }
        }

        SectionTitle("Acerca de")
        SurfaceCard(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 1.dp,
        ) {
            NavRow(
                "Versión ${BuildConfig.VERSION_NAME}",
                "Licencias de código abierto",
                icon = Icons.Filled.Info,
            ) { onNav(SettingsDest.About) }
        }

        SurfaceCard(
            onClick = { confirmLogout = true },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
            tonalElevation = 0.dp,
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        "Cerrar sesión",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                },
                supportingContent = {
                    Text(
                        "Borrar credenciales e historial de este dispositivo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Cerrar sesión") },
            text = { Text("Se revocará este dispositivo y se borrarán las credenciales y el historial local.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    onLogout()
                }) { Text("Cerrar sesión") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancelar") } },
        )
    }
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Tema de la app") },
            text = {
                Column {
                    RpThemeChoice.entries.forEach { choice ->
                        ListItem(
                            headlineContent = { Text(choice.label) },
                            trailingContent = {
                                if (state.settings.appTheme == choice.id) Text("✓", color = MaterialTheme.colorScheme.primary)
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable {
                                        onSelectTheme(choice.id)
                                        showThemeDialog = false
                                    },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Cerrar") }
            },
        )
    }
    if (showFontScaleDialog) {
        val options =
            listOf(
                0.85f to "Pequeño (85%)",
                1.00f to "Normal (100%)",
                1.15f to "Grande (115%)",
                1.30f to "Extra grande (130%)",
            )
        AlertDialog(
            onDismissRequest = { showFontScaleDialog = false },
            title = { Text("Tamaño de letra en el chat") },
            text = {
                Column {
                    options.forEach { (scale, label) ->
                        val isSelected = kotlin.math.abs(state.settings.chatFontScale - scale) < 0.04f
                        ListItem(
                            headlineContent = { Text(label) },
                            trailingContent = {
                                if (isSelected) Text("✓", color = MaterialTheme.colorScheme.primary)
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable {
                                        onSelectFontScale(scale)
                                        showFontScaleDialog = false
                                    },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFontScaleDialog = false }) { Text("Cerrar") }
            },
        )
    }
}

@Composable
fun SettingsScreen(
    nav: (SettingsDest) -> Unit,
    onLoggedOut: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    LifecycleRefresh { vm.refreshHealth() }
    SettingsContent(
        state = st,
        onNav = nav,
        onSelectTheme = vm::setAppTheme,
        onSelectFontScale = vm::setChatFontScale,
        onLogout = { vm.logout(onLoggedOut) },
    )
}

private fun themeLabel(id: String): String = RpThemeChoice.entries.find { it.id == id }?.label ?: "Sincronizado con Pi (automático)"

private fun fontScaleLabel(scale: Float): String =
    when {
        scale <= 0.90f -> "Pequeño (${(scale * 100).toInt()}%)"
        scale >= 1.25f -> "Extra grande (${(scale * 100).toInt()}%)"
        scale >= 1.08f -> "Grande (${(scale * 100).toInt()}%)"
        else -> "Normal (${(scale * 100).toInt()}%)"
    }

enum class SettingsDest { Devices, Machines, AddMachine, Notifications, Security, Battery, About }

private fun healthSummary(h: HealthState): String =
    when {
        !h.notificationsEnabled -> "⚠ Notificaciones desactivadas"
        h.approvalsMuted -> "⚠ Canal de autorizaciones silenciado"
        else -> "Categorías, PCs, horas silenciosas, prueba"
    }

@Composable
private fun NavRow(
    title: String,
    subtitle: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = icon?.let { { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary) } },
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun LifecycleRefresh(onResume: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) onResume() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
}

/** Plantilla de subpantalla con barra superior y snackbar de mensajes del ViewModel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreen(
    title: String,
    onBack: () -> Unit,
    vm: SettingsViewModel,
    content: @Composable () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val msg by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(msg) {
        msg?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }
    Scaffold(
        contentWindowInsets =
            androidx.compose.foundation.layout
                .WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets =
                    androidx.compose.foundation.layout
                        .WindowInsets(0, 0, 0, 0),
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) { content() }
    }
}

// ───────────────────────────── Dispositivos ─────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesContent(
    devices: List<DeviceInfo>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRevoke: (String) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var toRevoke by remember { mutableStateOf<String?>(null) }
    Scaffold(
        contentWindowInsets =
            androidx.compose.foundation.layout
                .WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets =
                    androidx.compose.foundation.layout
                        .WindowInsets(0, 0, 0, 0),
                title = { Text("Dispositivos") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            if (devices.isEmpty()) Text("Sin dispositivos", Modifier.padding(16.dp))
            devices.forEach { d ->
                ListItem(
                    headlineContent = { Text(d.name.ifBlank { d.deviceId } + if (d.current) " (este)" else "") },
                    supportingContent = { Text("Visto ${relativeTime(d.lastSeenAt).ifBlank { "—" }}") },
                    trailingContent = { if (!d.current) TextButton(onClick = { toRevoke = d.deviceId }) { Text("Revocar") } },
                )
            }
        }
    }
    toRevoke?.let { id ->
        AlertDialog(
            onDismissRequest = { toRevoke = null },
            title = { Text("Revocar dispositivo") },
            text = { Text("Dejará de recibir notificaciones y de poder conectarse.") },
            confirmButton = {
                TextButton(onClick = {
                    onRevoke(id)
                    toRevoke = null
                }) { Text("Revocar") }
            },
            dismissButton = { TextButton(onClick = { toRevoke = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun DevicesScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val devices by vm.devices.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val msg by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadDevices() }
    LaunchedEffect(msg) {
        msg?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }
    DevicesContent(
        devices = devices,
        onBack = onBack,
        onRevoke = vm::revokeDevice,
        snackbarHostState = snackbar,
    )
}

// ───────────────────────────── Máquinas ─────────────────────────────

private val PALETTE = listOf("#2E7D32", "#1565C0", "#6A1B9A", "#C62828", "#EF6C00", "#00838F", "#4E342E", "#455A64")

@Composable
fun MachinesScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshMachines() }
    var edit by remember { mutableStateOf<MachineSummary?>(null) }
    var revoke by remember { mutableStateOf<MachineSummary?>(null) }
    SubScreen("PCs / máquinas", onBack, vm) {
        if (st.machines.isEmpty()) Text("Aún no hay PCs emparejadas", Modifier.padding(16.dp))
        st.machines.forEach { m ->
            ListItem(
                leadingContent = { ColorDot(parseColor(m.color), size = 14) },
                headlineContent = { Text("${contextEmoji(m.context)} ${m.name}") },
                supportingContent = {
                    Text(
                        "${contextLabel(m.context)} · ${if (m.online) "en línea" else "sin conexión"} · ${m.agentCount} agentes",
                    )
                },
                trailingContent = {
                    Row {
                        TextButton(onClick = { edit = m }) { Text("Editar") }
                        TextButton(onClick = { revoke = m }) { Text("Revocar", color = MaterialTheme.colorScheme.error) }
                    }
                },
            )
        }
    }
    edit?.let { m ->
        EditMachineDialog(m, onDismiss = { edit = null }) { n, c, col ->
            vm.editMachine(m.machineId, n, c, col)
            edit = null
        }
    }
    revoke?.let { m ->
        AlertDialog(
            onDismissRequest = { revoke = null },
            title = { Text("Revocar ${m.name}") },
            text = { Text("La PC se desconecta al instante y sus agentes desaparecen de la lista.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.revokeMachine(m.machineId)
                    revoke = null
                }) { Text("Revocar") }
            },
            dismissButton = { TextButton(onClick = { revoke = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun EditMachineDialog(
    m: MachineSummary,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf(m.name) }
    var ctx by remember { mutableStateOf(m.context) }
    var color by remember { mutableStateOf(m.color) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar PC") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true)
                Text("Contexto", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(ctx == "personal", { ctx = "personal" }, label = { Text("🏠 Personal") })
                    FilterChip(ctx == "work", { ctx = "work" }, label = { Text("🏢 Empresa") })
                }
                OutlinedTextField(ctx, { ctx = it }, label = { Text("Otro (texto libre)") }, singleLine = true)
                Text("Color", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PALETTE.forEach { hex ->
                        Box2(parseColor(hex), selected = hex.equals(color, true)) { color = hex }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, ctx, color) }, enabled = name.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun Box2(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(if (selected) 32.dp else 26.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
    )
}

@Composable
fun AddMachineScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    val code by vm.pairCode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.createMachineCode() }
    SubScreen("Añadir máquina", onBack, vm) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("En la PC, dentro de Pi (con el plugin rp instalado), ejecuta:")
            val url = st.session?.relayUrl.orEmpty()
            val cmd = if (code != null) "/rp pair $url $code" else "Generando código…"
            Card(Modifier.fillMaxWidth()) {
                Text(cmd, Modifier.padding(16.dp), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
            Text(
                "Puedes añadir `--name \"PC Empresa\" --context work`. El código caduca en 10 minutos y es de un solo uso.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { copy(context, cmd) }, enabled = code != null) { Text("Copiar") }
                OutlinedButton(onClick = vm::createMachineCode) { Text("Nuevo código") }
            }
        }
    }
}

private fun copy(
    context: Context,
    text: String,
) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("rp", text))
}

// ───────────────────────────── Notificaciones ─────────────────────────────

@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleRefresh { vm.refreshHealth() }
    val prefs = st.settings.notifyPrefs
    SubScreen("Notificaciones", onBack, vm) {
        SectionTitle("Estado")
        StatusLine("Permiso de notificaciones", st.health.notificationsEnabled)
        StatusLine("Canal de autorizaciones sin silenciar", !st.health.approvalsMuted)
        StatusLine("Excluida de la optimización de batería", st.health.batteryExcluded)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }) { Text("Ajustes del sistema") }
            Button(onClick = vm::sendTestNotification) { Text("Enviar notificación de prueba") }
        }

        SectionTitle("Canal de entrega")
        SwitchRow(
            "Canal directo (sin Google)",
            "Servicio en primer plano con conexión al relay",
            st.settings.directPush,
        ) { vm.setDirectPush(it) }

        SectionTitle("Categorías")
        listOf(
            NotifyCategory.APPROVALS to "Autorizaciones",
            NotifyCategory.QUESTIONS to "Preguntas",
            NotifyCategory.TERMINAL to "Esperando en la terminal",
            NotifyCategory.FINISHED to "Agente terminó",
            NotifyCategory.ERRORS to "Errores y desconexiones",
        ).forEach { (key, label) ->
            SwitchRow(label, null, prefs.categories[key] ?: true) { vm.setCategory(key, it) }
        }
        SwitchRow("Autorizaciones críticas siempre", "Ignoran el silencio y las horas silenciosas", st.settings.criticalAlways) {
            vm.setCriticalAlways(it)
        }

        SectionTitle("Por agente")
        if (st.settings.mutedAgents.isEmpty()) {
            Text(
                "Ningún agente silenciado. Silencia uno desde el menú de su chat.",
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        st.settings.mutedAgents.forEach { id ->
            val a = st.agents.firstOrNull { it.agentId == id }
            ListItem(
                headlineContent = { Text(a?.let { agentTitle(it.sessionName, it.cwd, it.agentId) } ?: id) },
                supportingContent = { Text(a?.machineName ?: "agente desconocido") },
                trailingContent = { TextButton(onClick = { vm.unmuteAgent(id) }) { Text("Reactivar") } },
            )
        }

        SectionTitle("Por PC")
        if (st.machines.isEmpty()) Text("Sin PCs emparejadas", Modifier.padding(16.dp))
        st.machines.forEach { m -> MachinePrefsCard(m, prefs.machines[m.machineId], vm) }
    }
}

@Composable
private fun StatusLine(
    label: String,
    ok: Boolean,
) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Text(
                if (ok) "✓" else "✕",
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        },
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked, onChange) },
    )
}

@Composable
private fun MachinePrefsCard(
    m: MachineSummary,
    p: dev.j3pf.rp.protocol.MachinePrefs?,
    vm: SettingsViewModel,
) {
    var from by remember(p) { mutableStateOf(p?.quietHours?.from.orEmpty()) }
    var to by remember(p) { mutableStateOf(p?.quietHours?.to.orEmpty()) }
    val tz =
        p?.quietHours?.tz ?: java.util.TimeZone
            .getDefault()
            .id
    val valid = (from.isBlank() && to.isBlank()) || (TIME.matches(from) && TIME.matches(to))
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(parseColor(m.color), size = 12)
                Text("  ${contextEmoji(m.context)} ${m.name}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text("Silenciar")
                Switch(p?.mute ?: false, { vm.setMachineMute(m.machineId, it) })
            }
            Text("Horas silenciosas ($tz)", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    from,
                    { from = it },
                    Modifier.weight(1f),
                    label = { Text("Desde HH:mm") },
                    singleLine = true,
                    isError = !valid,
                )
                OutlinedTextField(
                    to,
                    { to = it },
                    Modifier.weight(1f),
                    label = { Text("Hasta HH:mm") },
                    singleLine = true,
                    isError = !valid,
                )
                TextButton(onClick = { vm.setQuietHours(m.machineId, from.trim(), to.trim(), tz) }, enabled = valid) { Text("Guardar") }
            }
            NotifyCategory.all.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(categoryLabel(c), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(p?.categories?.get(c) ?: true, { vm.setMachineCategory(m.machineId, c, it) })
                }
            }
        }
    }
}

private val TIME = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

private fun categoryLabel(c: String) =
    when (c) {
        NotifyCategory.APPROVALS -> "Autorizaciones"
        NotifyCategory.QUESTIONS -> "Preguntas"
        NotifyCategory.TERMINAL -> "Terminal"
        NotifyCategory.FINISHED -> "Terminó"
        else -> "Errores"
    }

// ───────────────────────────── Seguridad ─────────────────────────────

@Composable
fun SecuritySettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    SubScreen("Seguridad", onBack, vm) {
        SwitchRow(
            "Requerir desbloqueo para aprobar",
            "Las acciones de Permitir/Denegar de las notificaciones y de la hoja piden desbloqueo (Android 12+ en notificaciones)",
            st.settings.requireUnlockToApprove,
        ) { vm.setRequireUnlock(it) }
        SwitchRow("Bloquear la app al abrirla", "Pide biometría o PIN al abrir y tras 30 s en segundo plano", st.settings.appLock) {
            vm.setAppLock(it)
        }
        SwitchRow(
            "Ocultar contenido en capturas",
            "Activa FLAG_SECURE: sin capturas ni vista previa en recientes",
            st.settings.flagSecure,
        ) {
            vm.setFlagSecure(it)
        }
        Text(
            "El envío \"como tecleado\" siempre pide desbloqueo (válido 5 min) porque cuenta como autorización humana en el modo Auto de gentle-pi.",
            Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// ───────────────────────────── Batería ─────────────────────────────

@SuppressLint("BatteryLife") // Persistent relay WebSocket connection requires foreground battery optimization exemption.
@Composable
fun BatteryScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleRefresh { vm.refreshHealth() }
    SubScreen("Optimización de batería", onBack, vm) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (st.health.batteryExcluded) {
                    "RP está excluida de la optimización de batería ✓"
                } else {
                    "RP está sujeta a la optimización de batería"
                },
            )
            Text(
                "El canal directo mantiene una conexión permanente con tu relay. Algunos fabricantes (Xiaomi, Huawei, Oppo, Samsung…) " +
                    "cierran los servicios en segundo plano; excluir RP evita que lo hagan. Con FCM es solo una recomendación.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri()),
                )
            }, enabled = !st.health.batteryExcluded) { Text("Excluir RP de la optimización") }
            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) {
                Text("Abrir ajustes de batería")
            }
            Text(
                "Consejo: en Xiaomi/Oppo/Huawei activa también \"Inicio automático\" y bloquea la app en recientes.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// ───────────────────────────── Acerca de ─────────────────────────────

private val LICENSES =
    listOf(
        "AndroidX (Compose, Room, Navigation, WorkManager, DataStore, Biometric, CameraX, Lifecycle)" to "Apache 2.0",
        "Kotlin, kotlinx.coroutines, kotlinx.serialization" to "Apache 2.0",
        "OkHttp / Okio" to "Apache 2.0",
        "Dagger / Hilt" to "Apache 2.0",
        "Google Tink" to "Apache 2.0",
        "ML Kit Barcode Scanning" to "Términos de ML Kit (modelo empaquetado)",
        "Coil" to "Apache 2.0",
        "multiplatform-markdown-renderer (Mike Penz)" to "Apache 2.0",
        "Firebase Cloud Messaging (sólo sabor fcm)" to "Apache 2.0 / Términos de Firebase",
    )

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    SubScreen("Acerca de", onBack, vm) {
        LabeledText("Versión", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        LabeledText("Sabor de push", BuildConfig.FLAVOR)
        SectionTitle("Licencias")
        LICENSES.forEach { (n, l) -> ListItem(headlineContent = { Text(n) }, supportingContent = { Text(l) }) }
    }
}

@Composable
private fun LabeledText(
    label: String,
    value: String,
) {
    ListItem(headlineContent = { Text(label) }, trailingContent = { Text(value) })
}
