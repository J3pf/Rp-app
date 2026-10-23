package dev.j3pf.rp.data.repo

import dev.j3pf.rp.protocol.NotifyPrefs

/** Categorías de notificación (coinciden con los canales por PC). */
object NotifyCategory {
    const val APPROVALS = "approvals"
    const val QUESTIONS = "questions"
    const val TERMINAL = "terminal"
    const val FINISHED = "finished"
    const val ERRORS = "errors"
    val all = listOf(APPROVALS, QUESTIONS, TERMINAL, FINISHED, ERRORS)
}

data class AppSettings(
    /** `"all"` o el `context` de la PC seleccionada en el selector superior. */
    val selectedContext: String = ALL,
    val directPush: Boolean = true,
    val requireUnlockToApprove: Boolean = true,
    val criticalAlways: Boolean = true,
    val appLock: Boolean = false,
    val flagSecure: Boolean = false,
    val onboardingDone: Boolean = false,
    val batteryAsked: Boolean = false,
    val notifyPrefs: NotifyPrefs = NotifyPrefs(),
    /** Agentes silenciados (preferencia local por agente). */
    val mutedAgents: Set<String> = emptySet(),
    val appTheme: String = "auto",
    val chatFontScale: Float = 1.0f,
    val chatMessageSpacing: String = "normal",
    val selectedMeshMachine: String? = null,
    val selectedMeshGroup: String? = null,
) {
    companion object {
        const val ALL = "all"
    }
}
