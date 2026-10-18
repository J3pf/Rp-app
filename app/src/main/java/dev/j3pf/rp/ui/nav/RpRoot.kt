package dev.j3pf.rp.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.j3pf.rp.data.repo.PromptsRepository
import dev.j3pf.rp.ui.MainViewModel
import dev.j3pf.rp.ui.NavTarget
import dev.j3pf.rp.ui.agents.AgentsScreen
import dev.j3pf.rp.ui.chat.ChatScreen
import dev.j3pf.rp.ui.mesh.MeshScreen
import dev.j3pf.rp.ui.newagent.NewAgentScreen
import dev.j3pf.rp.ui.onboarding.OnboardingScreen
import dev.j3pf.rp.ui.onboarding.PairScreen
import dev.j3pf.rp.ui.onboarding.PermissionsScreen
import dev.j3pf.rp.ui.onboarding.ScanScreen
import dev.j3pf.rp.ui.pending.PendingScreen
import dev.j3pf.rp.ui.promptsheet.PromptSheet
import dev.j3pf.rp.ui.settings.AboutScreen
import dev.j3pf.rp.ui.settings.AddMachineScreen
import dev.j3pf.rp.ui.settings.BatteryScreen
import dev.j3pf.rp.ui.settings.DevicesScreen
import dev.j3pf.rp.ui.settings.MachinesScreen
import dev.j3pf.rp.ui.settings.NotificationSettingsScreen
import dev.j3pf.rp.ui.settings.SecuritySettingsScreen
import dev.j3pf.rp.ui.settings.SettingsDest
import dev.j3pf.rp.ui.settings.SettingsScreen
import dev.j3pf.rp.ui.theme.LocalReducedMotion
import dev.j3pf.rp.ui.theme.RpColors
import dev.j3pf.rp.ui.theme.RpMotion
import dev.j3pf.rp.ui.theme.rememberIsReducedMotion
import dev.j3pf.rp.util.BiometricGate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class BadgeViewModel
    @Inject
    constructor(
        prompts: PromptsRepository,
    ) : ViewModel() {
        val pending: StateFlow<Int> = prompts.open.map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    }

/** Raíz de la UI: navegación tipada, barra inferior [Agentes][Pendientes (badge)][Ajustes] y hoja de respuesta global. */
@Composable
fun RpRoot(
    mainVm: MainViewModel,
    gate: BiometricGate,
    badgeVm: BadgeViewModel = hiltViewModel(),
) {
    val session by mainVm.session.collectAsStateWithLifecycle()
    val settings by mainVm.settings.collectAsStateWithLifecycle()
    val sheetPrompt by mainVm.sheetPrompt.collectAsStateWithLifecycle()
    val target by mainVm.target.collectAsStateWithLifecycle()
    val pendingCount by badgeVm.pending.collectAsStateWithLifecycle()
    val nav = rememberNavController()

    // Esperamos a que carguen los ajustes para no parpadear entre onboarding y agentes.
    val loaded = settings ?: return

    val start: Any =
        when {
            session == null -> OnboardingRoute
            !loaded.onboardingDone -> PermissionsRoute
            else -> AgentsRoute
        }

    // Si se pierde la sesión (cerrar sesión / revocación) volvemos al onboarding.
    var hadSession by remember { mutableStateOf(session != null) }
    LaunchedEffect(session == null) {
        if (session != null) {
            hadSession = true
        } else if (hadSession) {
            hadSession = false
            nav.navigate(OnboardingRoute) { popUpTo(nav.graph.findStartDestination().id) { inclusive = true } }
        }
    }

    // Destinos pedidos desde notificaciones / deep links.
    LaunchedEffect(target, session) {
        val t = target ?: return@LaunchedEffect
        when (t) {
            is NavTarget.Pair -> {
                nav.navigate(PairRoute(t.url, t.code))
                mainVm.consumeTarget()
            }

            is NavTarget.Agent -> {
                if (session != null) {
                    nav.navigate(ChatRoute(t.agentId)) { launchSingleTop = true }
                    mainVm.consumeTarget()
                }
            }

            is NavTarget.Prompt -> {
                if (session != null) {
                    t.agentId?.let { nav.navigate(ChatRoute(it)) { launchSingleTop = true } }
                    mainVm.openPrompt(t.promptId)
                    mainVm.consumeTarget()
                }
            }
        }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val dest = backStack?.destination
    val showBar =
        dest?.hasRoute<AgentsRoute>() == true ||
            dest?.hasRoute<MeshRoute>() == true ||
            dest?.hasRoute<PendingRoute>() == true ||
            dest?.hasRoute<SettingsRoute>() == true

    val reducedMotion = rememberIsReducedMotion()

    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        Scaffold(
            bottomBar = { if (showBar) BottomBar(nav, pendingCount) },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = start,
                modifier = Modifier.padding(padding),
                enterTransition = { if (reducedMotion) EnterTransition.None else RpMotion.navEnter },
                exitTransition = { if (reducedMotion) ExitTransition.None else RpMotion.navExit },
                popEnterTransition = { if (reducedMotion) EnterTransition.None else RpMotion.navPopEnter },
                popExitTransition = { if (reducedMotion) ExitTransition.None else RpMotion.navPopExit },
            ) {
                composable<OnboardingRoute> { OnboardingScreen(onStart = { nav.navigate(ScanRoute) }) }
                composable<ScanRoute> {
                    ScanScreen(
                        onResult = { u, c -> nav.navigate(PairRoute(u, c)) { popUpTo<ScanRoute> { inclusive = true } } },
                        onManual = { nav.navigate(PairRoute()) { popUpTo<ScanRoute> { inclusive = true } } },
                    )
                }
                composable<PairRoute> { e ->
                    val r = e.toRoute<PairRoute>()
                    PairScreen(
                        initialUrl = r.url,
                        initialCode = r.code,
                        onScan = { nav.navigate(ScanRoute) },
                        onPaired = { nav.navigate(PermissionsRoute) { popUpTo(nav.graph.findStartDestination().id) { inclusive = true } } },
                    )
                }
                composable<PermissionsRoute> {
                    PermissionsScreen(onDone = { nav.navigate(AgentsRoute) { popUpTo(nav.graph.id) { inclusive = true } } })
                }
                composable<AgentsRoute> {
                    AgentsScreen(
                        onOpenChat = { nav.navigate(ChatRoute(it)) },
                        onNewAgent = { nav.navigate(NewAgentRoute) },
                        mainVm = mainVm,
                    )
                }
                composable<MeshRoute> {
                    MeshScreen(onOpenChat = { nav.navigate(ChatRoute(it)) })
                }
                composable<PendingRoute> { PendingScreen(onOpenPrompt = mainVm::openPrompt) }
                composable<SettingsRoute> {
                    SettingsScreen(
                        nav = { d ->
                            nav.navigate(
                                when (d) {
                                    SettingsDest.Devices -> DevicesRoute
                                    SettingsDest.Machines -> MachinesRoute
                                    SettingsDest.AddMachine -> AddMachineRoute
                                    SettingsDest.Notifications -> NotificationSettingsRoute
                                    SettingsDest.Security -> SecuritySettingsRoute
                                    SettingsDest.Battery -> BatteryRoute
                                    SettingsDest.About -> AboutRoute
                                },
                            )
                        },
                        onLoggedOut = {},
                    )
                }
                composable<ChatRoute> { ChatScreen(onBack = { nav.popBackStack() }) }
                composable<NewAgentRoute> { NewAgentScreen(onBack = { nav.popBackStack() }, onCreated = { nav.popBackStack() }) }
                composable<DevicesRoute> { DevicesScreen(onBack = { nav.popBackStack() }) }
                composable<MachinesRoute> { MachinesScreen(onBack = { nav.popBackStack() }) }
                composable<AddMachineRoute> { AddMachineScreen(onBack = { nav.popBackStack() }) }
                composable<NotificationSettingsRoute> { NotificationSettingsScreen(onBack = { nav.popBackStack() }) }
                composable<SecuritySettingsRoute> { SecuritySettingsScreen(onBack = { nav.popBackStack() }) }
                composable<BatteryRoute> { BatteryScreen(onBack = { nav.popBackStack() }) }
                composable<AboutRoute> { AboutScreen(onBack = { nav.popBackStack() }) }
            }
        }
    }

    sheetPrompt?.let { id -> PromptSheet(promptId = id, onDismiss = mainVm::closePrompt) }
}

enum class AppTab(
    val route: Any,
    val label: String,
) {
    AGENTS(AgentsRoute, "Agentes"),
    MESH(MeshRoute, "Red"),
    PENDING(PendingRoute, "Pendientes"),
    SETTINGS(SettingsRoute, "Ajustes"),
}

@Composable
fun AppBottomBar(
    selectedTab: AppTab,
    modifier: Modifier = Modifier,
    pending: Int = 0,
    onSelectTab: (AppTab) -> Unit = {},
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            AppTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                NavigationBarItem(
                    selected = selected,
                    alwaysShowLabel = true,
                    onClick = { onSelectTab(tab) },
                    icon = {
                        when (tab) {
                            AppTab.PENDING -> {
                                BadgedBox(
                                    badge = {
                                        if (pending > 0) {
                                            Badge(containerColor = RpColors.warning) {
                                                Text("$pending", color = MaterialTheme.colorScheme.onPrimary)
                                            }
                                        }
                                    },
                                ) {
                                    Icon(Icons.Filled.Inbox, contentDescription = tab.label)
                                }
                            }

                            AppTab.AGENTS -> {
                                Icon(Icons.Filled.SmartToy, contentDescription = tab.label)
                            }

                            AppTab.MESH -> {
                                Icon(Icons.Filled.Hub, contentDescription = tab.label)
                            }

                            AppTab.SETTINGS -> {
                                Icon(Icons.Filled.Settings, contentDescription = tab.label)
                            }
                        }
                    },
                    label = {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    colors =
                        NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                )
            }
        }
    }
}

@Composable
internal fun BottomBar(
    nav: NavHostController,
    pending: Int,
) {
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val activeTab =
        when {
            dest?.hasRoute<AgentsRoute>() == true -> AppTab.AGENTS
            dest?.hasRoute<MeshRoute>() == true -> AppTab.MESH
            dest?.hasRoute<PendingRoute>() == true -> AppTab.PENDING
            dest?.hasRoute<SettingsRoute>() == true -> AppTab.SETTINGS
            else -> AppTab.AGENTS
        }
    AppBottomBar(
        selectedTab = activeTab,
        pending = pending,
        onSelectTab = { tab ->
            nav.navigate(tab.route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
    )
}

/** Pantalla de bloqueo biométrico de la app (fase 4). */
@Composable
fun LockScreen(
    onUnlock: () -> Unit,
    message: String? = null,
) {
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("RP bloqueada", style = MaterialTheme.typography.headlineMedium)
                message?.let { Text(it, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error) }
                Button(onClick = onUnlock, Modifier.padding(top = 24.dp)) { Text("Desbloquear") }
            }
        }
    }
}
