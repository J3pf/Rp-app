package dev.j3pf.rp.ui.settings

import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.data.repo.NotifyCategory
import dev.j3pf.rp.data.secure.Session
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.ui.theme.RpThemeChoice

/** Estado de las condiciones que hacen fiables las notificaciones (pantalla de Ajustes). */
data class HealthState(
    val notificationsEnabled: Boolean = true,
    val approvalsMuted: Boolean = false,
    val batteryExcluded: Boolean = false,
)

data class SettingsUiState(
    val session: Session? = null,
    val conn: ConnState = ConnState.Disconnected,
    val settings: AppSettings = AppSettings(),
    val machines: List<MachineSummary> = emptyList(),
    val health: HealthState = HealthState(),
    val agents: List<AgentSummary> = emptyList(),
)

enum class SettingsDest {
    Devices,
    Machines,
    AddMachine,
    Notifications,
    Security,
    Battery,
    About,
}

fun themeLabel(id: String): String = RpThemeChoice.entries.find { it.id == id }?.label ?: "Sincronizado con Pi (automático)"

fun fontScaleLabel(scale: Float): String =
    when {
        scale <= 0.90f -> "Pequeño (${(scale * 100).toInt()}%)"
        scale >= 1.25f -> "Extra grande (${(scale * 100).toInt()}%)"
        scale >= 1.08f -> "Grande (${(scale * 100).toInt()}%)"
        else -> "Normal (${(scale * 100).toInt()}%)"
    }

fun messageSpacingLabel(spacing: String): String =
    when (spacing) {
        "compact" -> "Compacto"
        "spacious" -> "Amplio"
        else -> "Normal"
    }

fun healthSummary(h: HealthState): String =
    when {
        !h.notificationsEnabled -> "⚠ Notificaciones desactivadas"
        h.approvalsMuted -> "⚠ Canal de autorizaciones silenciado"
        else -> "Categorías, PCs, horas silenciosas, prueba"
    }

fun categoryLabel(c: String): String =
    when (c) {
        NotifyCategory.APPROVALS -> "Autorizaciones"
        NotifyCategory.QUESTIONS -> "Preguntas"
        NotifyCategory.TERMINAL -> "Terminal"
        NotifyCategory.FINISHED -> "Terminó"
        else -> "Errores"
    }

val MACHINE_COLOR_PALETTE =
    listOf(
        "#2E7D32",
        "#1565C0",
        "#6A1B9A",
        "#C62828",
        "#EF6C00",
        "#00838F",
        "#4E342E",
        "#455A64",
    )

val DEFAULT_LICENSES =
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
