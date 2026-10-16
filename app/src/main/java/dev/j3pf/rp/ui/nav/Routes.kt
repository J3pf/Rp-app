package dev.j3pf.rp.ui.nav

import kotlinx.serialization.Serializable

@Serializable data object OnboardingRoute
@Serializable data class PairRoute(val url: String? = null, val code: String? = null)
@Serializable data object ScanRoute
@Serializable data object PermissionsRoute
@Serializable data object AgentsRoute
@Serializable data object MeshRoute
@Serializable data object PendingRoute
@Serializable data object SettingsRoute
@Serializable data class ChatRoute(val agentId: String)
@Serializable data object NewAgentRoute
@Serializable data object DevicesRoute
@Serializable data object MachinesRoute
@Serializable data object AddMachineRoute
@Serializable data object NotificationSettingsRoute
@Serializable data object SecuritySettingsRoute
@Serializable data object BatteryRoute
@Serializable data object AboutRoute
