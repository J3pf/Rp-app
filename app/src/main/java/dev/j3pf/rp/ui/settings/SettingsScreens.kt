package dev.j3pf.rp.ui.settings

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.j3pf.rp.BuildConfig
import java.util.TimeZone

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
        onSelectMessageSpacing = vm::setChatMessageSpacing,
        onLogout = { vm.logout(onLoggedOut) },
        versionName = BuildConfig.VERSION_NAME,
    )
}

@Composable
private fun LifecycleRefresh(onResume: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) onResume() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
}

// ───────────────────────────── Dispositivos ─────────────────────────────

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

@Composable
fun MachinesScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val msg by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshMachines() }
    LaunchedEffect(msg) {
        msg?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }
    MachinesContent(
        machines = st.machines,
        onBack = onBack,
        onEditMachine = { id, name, ctx, col -> vm.editMachine(id, name, ctx, col) },
        onRevokeMachine = vm::revokeMachine,
        snackbarHostState = snackbar,
    )
}

// ───────────────────────────── Añadir máquina ─────────────────────────────

@Composable
fun AddMachineScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    val code by vm.pairCode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val msg by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.createMachineCode() }
    LaunchedEffect(msg) {
        msg?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }
    AddMachineContent(
        relayUrl = st.session?.relayUrl.orEmpty(),
        pairCode = code,
        onBack = onBack,
        onCopyCommand = { copy(context, it) },
        onNewCode = vm::createMachineCode,
        snackbarHostState = snackbar,
    )
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
    val snackbar = remember { SnackbarHostState() }
    val msg by vm.message.collectAsStateWithLifecycle()
    LifecycleRefresh { vm.refreshHealth() }
    LaunchedEffect(msg) {
        msg?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }
    val defaultTz = TimeZone.getDefault().id
    NotificationSettingsContent(
        health = st.health,
        settings = st.settings,
        machines = st.machines,
        agents = st.agents,
        onBack = onBack,
        onOpenSystemSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        },
        onSendTestNotification = vm::sendTestNotification,
        onSetDirectPush = vm::setDirectPush,
        onSetCategory = { cat, enabled -> vm.setCategory(cat, enabled) },
        onSetCriticalAlways = vm::setCriticalAlways,
        onUnmuteAgent = vm::unmuteAgent,
        onSetMachineMute = { machineId, mute -> vm.setMachineMute(machineId, mute) },
        onSetQuietHours = { machineId, from, to, tz -> vm.setQuietHours(machineId, from, to, tz) },
        onSetMachineCategory = { machineId, cat, enabled -> vm.setMachineCategory(machineId, cat, enabled) },
        defaultTz = defaultTz,
        snackbarHostState = snackbar,
    )
}

// ───────────────────────────── Seguridad ─────────────────────────────

@Composable
fun SecuritySettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val msg by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(msg) {
        msg?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }
    SecurityContent(
        settings = st.settings,
        onBack = onBack,
        onSetRequireUnlock = vm::setRequireUnlock,
        onSetAppLock = vm::setAppLock,
        onSetFlagSecure = vm::setFlagSecure,
        snackbarHostState = snackbar,
    )
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
    val snackbar = remember { SnackbarHostState() }
    val msg by vm.message.collectAsStateWithLifecycle()
    LifecycleRefresh { vm.refreshHealth() }
    LaunchedEffect(msg) {
        msg?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }
    BatteryContent(
        batteryExcluded = st.health.batteryExcluded,
        onBack = onBack,
        onRequestIgnoreBatteryOptimizations = {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri()),
            )
        },
        onOpenBatterySettings = {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        },
        snackbarHostState = snackbar,
    )
}

// ───────────────────────────── Acerca de ─────────────────────────────

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val snackbar = remember { SnackbarHostState() }
    val msg by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(msg) {
        msg?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }
    AboutContent(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE.toString(),
        flavor = BuildConfig.FLAVOR,
        onBack = onBack,
        snackbarHostState = snackbar,
    )
}
