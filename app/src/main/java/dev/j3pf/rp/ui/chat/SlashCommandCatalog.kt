package dev.j3pf.rp.ui.chat

import dev.j3pf.rp.protocol.AgentSummary

/**
 * Catálogo de comandos slash (/) adaptado dinámicamente según el entorno o lanzador (launcher)
 * del terminal o agente (Antigravity, Claude, Gentle Shell, Pi, o Shell genérico).
 */
data class SlashCommandCatalog(
    val launcherKey: String,
    val launcherLabel: String,
    val commands: List<String>,
    val quickShortcuts: List<String>,
    val subSuggestions: Map<String, List<String>> = emptyMap(),
)

object SlashCommandCatalogs {
    val PI =
        SlashCommandCatalog(
            launcherKey = "pi",
            launcherLabel = "Pi",
            commands =
                listOf(
                    "/clear",
                    "/compact",
                    "/model",
                    "/thinking",
                    "/quit",
                    "/undo",
                    "/sdd-status",
                    "/status",
                    "/help",
                    "/rp",
                ),
            quickShortcuts = listOf("/status", "/compact", "/model", "/thinking", "/clear"),
            subSuggestions =
                mapOf(
                    "/thinking" to listOf("off", "minimal", "low", "medium", "high", "xhigh", "max"),
                ),
        )

    val ANTIGRAVITY =
        SlashCommandCatalog(
            launcherKey = "agy",
            launcherLabel = "Antigravity",
            commands =
                listOf(
                    "/goal",
                    "/plan",
                    "/compact",
                    "/schedule",
                    "/browser",
                    "/grill-me",
                    "/teamwork-preview",
                    "/learn",
                    "/boost",
                    "/clear",
                    "/status",
                    "/help",
                    "/quit",
                ),
            quickShortcuts = listOf("/goal", "/plan", "/compact", "/browser", "/schedule", "/status"),
            subSuggestions =
                mapOf(
                    "/schedule" to listOf("timer", "cron", "cancel", "status"),
                ),
        )

    val CLAUDE =
        SlashCommandCatalog(
            launcherKey = "claude",
            launcherLabel = "Claude",
            commands =
                listOf(
                    "/compact",
                    "/clear",
                    "/help",
                    "/cost",
                    "/doctor",
                    "/init",
                    "/review",
                    "/pr_comments",
                    "/bug",
                    "/status",
                    "/quit",
                ),
            quickShortcuts = listOf("/compact", "/clear", "/cost", "/review", "/help"),
        )

    val GENTLE_SHELL =
        SlashCommandCatalog(
            launcherKey = "gentle-shell",
            launcherLabel = "Gentle Shell",
            commands =
                listOf(
                    "/mode",
                    "/session",
                    "/compact",
                    "/clear",
                    "/status",
                    "/undo",
                    "/help",
                    "/quit",
                ),
            quickShortcuts = listOf("/mode", "/compact", "/status", "/clear", "/help"),
            subSuggestions =
                mapOf(
                    "/mode" to listOf("auto", "plan", "code", "ask"),
                ),
        )

    val CODEX =
        SlashCommandCatalog(
            launcherKey = "codex",
            launcherLabel = "Codex",
            commands =
                listOf(
                    "/compact",
                    "/clear",
                    "/review",
                    "/model",
                    "/status",
                    "/help",
                    "/quit",
                ),
            quickShortcuts = listOf("/review", "/status", "/compact", "/model", "/help"),
            subSuggestions =
                mapOf(
                    "/review" to listOf("uncommitted", "branch", "staged"),
                    "/model" to listOf("o3", "o1", "gpt-4o"),
                ),
        )

    val TERMINAL =
        SlashCommandCatalog(
            launcherKey = "terminal",
            launcherLabel = "Terminal",
            commands = listOf("/clear", "/status", "/compact", "/help", "/quit"),
            quickShortcuts = listOf("/status", "/clear", "/compact", "/help"),
        )

    fun resolve(agent: AgentSummary?): SlashCommandCatalog {
        if (agent == null) return PI

        val launcher = agent.launcher?.lowercase()?.trim()
        val modelId =
            agent.model
                ?.id
                ?.lowercase()
                ?.trim()
        val provider =
            agent.model
                ?.provider
                ?.lowercase()
                ?.trim()

        return when {
            launcher == "agy" || launcher == "antigravity" || modelId == "antigravity" -> ANTIGRAVITY
            launcher == "claude" || provider == "anthropic" || modelId?.contains("claude") == true -> CLAUDE
            launcher == "codex" || provider == "openai" || modelId?.contains("codex") == true -> CODEX
            launcher == "gentle-shell" || launcher == "opencode" -> GENTLE_SHELL
            launcher == "bash" || launcher == "zsh" || launcher == "sh" || launcher == "tmux" || launcher == "terminal" -> TERMINAL
            launcher == "pi" -> PI
            else -> PI
        }
    }
}
