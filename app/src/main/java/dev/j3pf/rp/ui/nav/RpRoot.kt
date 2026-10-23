package dev.j3pf.rp.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

    fun isBottomTab(d: androidx.navigation.NavDestination?): Boolean =
        d?.hasRoute<AgentsRoute>() == true ||
            d?.hasRoute<MeshRoute>() == true ||
            d?.hasRoute<PendingRoute>() == true ||
            d?.hasRoute<SettingsRoute>() == true

    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        Scaffold(
            bottomBar = {
                AnimatedVisibility(
                    visible = showBar,
                    enter =
                        slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                        ) + fadeIn(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)),
                    exit =
                        slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                        ) + fadeOut(animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)),
                ) {
                    BottomBar(nav, pendingCount)
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = start,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                enterTransition = {
                    if (reducedMotion) {
                        EnterTransition.None
                    } else if (isBottomTab(initialState.destination) && isBottomTab(targetState.destination)) {
                        RpMotion.tabEnter
                    } else {
                        RpMotion.detailEnter
                    }
                },
                exitTransition = {
                    if (reducedMotion) {
                        ExitTransition.None
                    } else if (isBottomTab(initialState.destination) && isBottomTab(targetState.destination)) {
                        RpMotion.tabExit
                    } else {
                        RpMotion.detailExit
                    }
                },
                popEnterTransition = {
                    if (reducedMotion) {
                        EnterTransition.None
                    } else if (isBottomTab(initialState.destination) && isBottomTab(targetState.destination)) {
                        RpMotion.tabEnter
                    } else {
                        RpMotion.detailPopEnter
                    }
                },
                popExitTransition = {
                    if (reducedMotion) {
                        ExitTransition.None
                    } else if (isBottomTab(initialState.destination) && isBottomTab(targetState.destination)) {
                        RpMotion.tabExit
                    } else {
                        RpMotion.detailPopExit
                    }
                },
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
