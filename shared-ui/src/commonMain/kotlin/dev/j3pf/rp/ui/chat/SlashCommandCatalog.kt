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
    val descriptions: Map<String, String> = emptyMap(),
)

object SlashCommandCatalogs {
    private val TERM_SUB_SUGGESTIONS = listOf("enter", "esc", "ctrl+c", "tab", "y", "n")

    private val COMMON_DESCRIPTIONS =
        mapOf(
            "/term" to "Terminal en vivo",
            "/usage" to "Cuota y consumo",
            "/compact" to "Compactar contexto",
            "/plan" to "Modo plan",
            "/goal" to "Modo meta continua",
            "/model" to "Cambiar modelo",
            "/thinking" to "Nivel de razonamiento",
            "/status" to "Estado de sesión",
            "/statusline" to "Barra de estado",
            "/clear" to "Limpiar contexto",
            "/review" to "Revisar cambios",
            "/diff" to "Ver diff",
            "/cost" to "Costo de sesión",
            "/doctor" to "Diagnóstico",
            "/mode" to "Cambiar modo",
            "/undo" to "Deshacer último paso",
            "/schedule" to "Programar tarea",
            "/browser" to "Navegador web",
            "/help" to "Ayuda",
            "/quit" to "Cerrar agente",
        )

    val PI =
        SlashCommandCatalog(
            launcherKey = "pi",
            launcherLabel = "Pi",
            commands =
                listOf(
                    "/status",
                    "/compact",
                    "/model",
                    "/thinking",
                    "/term",
                    "/clear",
                    "/undo",
                    "/sdd-status",
                    "/help",
                    "/rp",
                    "/quit",
                ),
            quickShortcuts = listOf("/status", "/compact", "/model", "/thinking", "/term", "/clear"),
            subSuggestions =
                mapOf(
                    "/thinking" to listOf("off", "minimal", "low", "medium", "high", "xhigh", "max"),
                    "/term" to TERM_SUB_SUGGESTIONS,
                ),
            descriptions = COMMON_DESCRIPTIONS,
        )

    val ANTIGRAVITY =
        SlashCommandCatalog(
            launcherKey = "agy",
            launcherLabel = "Antigravity",
            commands =
                listOf(
                    "/usage",
                    "/term",
                    "/goal",
                    "/plan",
                    "/compact",
                    "/schedule",
                    "/browser",
                    "/grill-me",
                    "/teamwork-preview",
                    "/learn",
                    "/boost",
                    "/statusline",
                    "/model",
                    "/clear",
                    "/help",
                    "/quit",
                ),
            quickShortcuts = listOf("/usage", "/term", "/goal", "/plan", "/compact", "/browser", "/schedule"),
            subSuggestions =
                mapOf(
                    "/schedule" to listOf("timer", "cron", "cancel", "status"),
                    "/term" to TERM_SUB_SUGGESTIONS,
                ),
            descriptions = COMMON_DESCRIPTIONS,
        )

    val CLAUDE =
        SlashCommandCatalog(
            launcherKey = "claude",
            launcherLabel = "Claude",
            commands =
                listOf(
                    "/compact",
                    "/term",
                    "/cost",
                    "/review",
                    "/status",
                    "/doctor",
                    "/clear",
                    "/init",
                    "/pr_comments",
                    "/bug",
                    "/help",
                    "/quit",
                ),
            quickShortcuts = listOf("/compact", "/term", "/cost", "/review", "/clear", "/help"),
            subSuggestions =
                mapOf(
                    "/term" to TERM_SUB_SUGGESTIONS,
                ),
            descriptions = COMMON_DESCRIPTIONS,
        )

    val GENTLE_SHELL =
        SlashCommandCatalog(
            launcherKey = "gentle-shell",
            launcherLabel = "Gentle Shell",
            commands =
                listOf(
                    "/mode",
                    "/term",
                    "/session",
                    "/compact",
                    "/clear",
                    "/status",
                    "/undo",
                    "/help",
                    "/quit",
                ),
            quickShortcuts = listOf("/mode", "/term", "/compact", "/status", "/clear", "/help"),
            subSuggestions =
                mapOf(
                    "/mode" to listOf("auto", "plan", "code", "ask"),
                    "/term" to TERM_SUB_SUGGESTIONS,
                ),
            descriptions = COMMON_DESCRIPTIONS,
        )

    val CODEX =
        SlashCommandCatalog(
            launcherKey = "codex",
            launcherLabel = "Codex",
            commands =
                listOf(
                    "/review",
                    "/term",
                    "/model",
                    "/diff",
                    "/approvals",
                    "/mcp",
                    "/compact",
                    "/undo",
                    "/fork",
                    "/resume",
                    "/clear",
                    "/status",
                    "/help",
                    "/quit",
                ),
            quickShortcuts = listOf("/review", "/term", "/model", "/diff", "/compact", "/status", "/help"),
            subSuggestions =
                mapOf(
                    "/review" to listOf("uncommitted", "branch", "staged"),
                    "/model" to listOf("o3", "o3-mini", "o1", "gpt-4o"),
                    "/approvals" to listOf("on-request", "never"),
                    "/term" to TERM_SUB_SUGGESTIONS,
                ),
            descriptions = COMMON_DESCRIPTIONS,
        )

    val TERMINAL =
        SlashCommandCatalog(
            launcherKey = "terminal",
            launcherLabel = "Terminal",
            commands = listOf("/term", "/status", "/clear", "/compact", "/help", "/quit"),
            quickShortcuts = listOf("/term", "/status", "/clear", "/compact", "/help"),
            subSuggestions =
                mapOf(
                    "/term" to TERM_SUB_SUGGESTIONS,
                ),
            descriptions = COMMON_DESCRIPTIONS,
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
