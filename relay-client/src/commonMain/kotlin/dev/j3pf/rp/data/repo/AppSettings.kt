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

    const val WIRE_PERMISSION = "permission"
    const val WIRE_QUESTION = "question"
    const val WIRE_TERMINAL_WAIT = "terminal_wait"
    const val WIRE_SETTLED = "settled"
    const val WIRE_ERROR = "error"

    private val localToWire =
        mapOf(
            APPROVALS to WIRE_PERMISSION,
            QUESTIONS to WIRE_QUESTION,
            TERMINAL to WIRE_TERMINAL_WAIT,
            FINISHED to WIRE_SETTLED,
            ERRORS to WIRE_ERROR,
        )

    /**
     * Maps persisted local category keys (Android channel names) to canonical `Rp-relay` wire keys,
     * passes through already-canonical keys, and strips stray `"criticalAlways"` entries.
     */
    fun toWireCategories(local: Map<String, Boolean>): Map<String, Boolean> {
        if (local.isEmpty()) return emptyMap()
        val out = linkedMapOf<String, Boolean>()
        for ((key, enabled) in local) {
            if (key == "criticalAlways") continue
            val wireKey = localToWire[key] ?: key
            out[wireKey] = enabled
        }
        return out
    }
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
