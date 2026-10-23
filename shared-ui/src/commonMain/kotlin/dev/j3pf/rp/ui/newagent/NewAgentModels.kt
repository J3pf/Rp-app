package dev.j3pf.rp.ui.newagent

import dev.j3pf.rp.protocol.HerdrLayout
import dev.j3pf.rp.protocol.HostInfo
import dev.j3pf.rp.protocol.MachineSummary

/** Dónde crear el agente cuando la PC tiene herdr (PLAN-APP §3). */
enum class HerdrPlacement {
    NEW_WORKSPACE,
    TAB,
    SPLIT,
}

data class NewAgentForm(
    val machineId: String? = null,
    val path: String = "",
    val dirs: List<String> = emptyList(),
    val loadingDirs: Boolean = false,
    val launcher: String? = null,
    val backend: String? = null,
    val placement: HerdrPlacement = HerdrPlacement.NEW_WORKSPACE,
    val layout: HerdrLayout? = null,
    val loadingLayout: Boolean = false,
    val workspaceId: String? = null,
    val paneId: String? = null,
    val direction: String = "right",
    val name: String = "",
    val initialPrompt: String = "",
    val spawning: Boolean = false,
    val error: String? = null,
    val created: Boolean = false,
)

data class NewAgentUi(
    val machines: List<MachineSummary> = emptyList(),
    val host: HostInfo? = null,
    val form: NewAgentForm = NewAgentForm(),
) {
    val hasHerdr: Boolean get() = host?.backends?.herdr != null

    val backends: List<String>
        get() =
            buildList {
                host?.backends?.let { b ->
                    if (b.herdr != null) add("herdr")
                    if (b.tmux != null) add("tmux")
                    if (b.wt) add("wt")
                    if (b.console) add("console")
                }
            }

    val effectiveBackend: String?
        get() = form.backend ?: host?.defaultBackend ?: backends.firstOrNull()

    val canCreate: Boolean
        get() =
            form.machineId != null &&
                form.path.isNotBlank() &&
                !form.spawning &&
                (
                    effectiveBackend != "herdr" ||
                        form.placement == HerdrPlacement.NEW_WORKSPACE ||
                        (form.placement == HerdrPlacement.TAB && form.workspaceId != null) ||
                        (form.placement == HerdrPlacement.SPLIT && form.paneId != null)
                )
}

fun backendLabel(b: String): String =
    when (b) {
        "herdr" -> "herdr"
        "tmux" -> "tmux"
        "wt" -> "Windows Terminal"
        "console" -> "Consola"
        else -> b
    }
