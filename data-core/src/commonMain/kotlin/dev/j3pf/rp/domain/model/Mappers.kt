package dev.j3pf.rp.domain.model

import dev.j3pf.rp.data.db.AgentEntity
import dev.j3pf.rp.data.db.MachineEntity
import dev.j3pf.rp.data.db.PromptEntity
import dev.j3pf.rp.data.db.PromptState
import dev.j3pf.rp.protocol.AgentStatus
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.Danger
import dev.j3pf.rp.protocol.HerdrInfo
import dev.j3pf.rp.protocol.MachineQuota
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.ModelInfo
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.RpJson
import dev.j3pf.rp.protocol.parseHerdrTabMeta
import kotlinx.serialization.builtins.ListSerializer

fun MachineSummary.toEntity(relayId: String) =
    MachineEntity(
        relayId,
        machineId,
        name,
        context,
        color,
        icon,
        online,
        agentCount,
        openPromptCount,
        hostOnline,
        quotasJson = quotas?.let { RpJson.encodeToString(ListSerializer(MachineQuota.serializer()), it) },
    )

fun MachineEntity.toModel() =
    MachineSummary(
        machineId = machineId,
        name = name,
        context = context,
        color = color,
        icon = icon,
        online = online,
        agentCount = agentCount,
        openPromptCount = openPromptCount,
        hostOnline = hostOnline,
        quotas =
            quotasJson?.let {
                runCatching { RpJson.decodeFromString(ListSerializer(MachineQuota.serializer()), it) }.getOrNull()
            },
    )

private val TAB_CONTEXT_META_REGEX = Regex("@\\d+/\\d+(?=\\|)")

fun AgentSummary.toEntity(relayId: String): AgentEntity {
    val rawTab = herdr?.tabId
    val encodedTab =
        if (contextTokens != null && contextWindow != null && contextWindow!! > 0) {
            when {
                rawTab == null -> "@$contextTokens/$contextWindow|"
                rawTab.contains('|') -> {
                    val stripped = rawTab.replace(TAB_CONTEXT_META_REGEX, "")
                    val pipeIdx = stripped.indexOf('|')
                    "${stripped.substring(0, pipeIdx)}@$contextTokens/$contextWindow${stripped.substring(pipeIdx)}"
                }
                else -> "@$contextTokens/$contextWindow|$rawTab"
            }
        } else {
            rawTab
        }
    return AgentEntity(
        relayId = relayId,
        agentId = agentId,
        machineId = machineId,
        machineName = machineName,
        machineContext = machineContext,
        machineColor = machineColor,
        sessionName = sessionName,
        cwd = cwd,
        gitBranch = gitBranch,
        model = model?.label?.ifEmpty { null },
        status = status,
        online = online,
        lastSeenAt = lastSeenAt,
        lastMessagePreview = lastMessagePreview,
        launcher = launcher,
        herdrPaneId = herdr?.paneId,
        herdrTabId = encodedTab,
        herdrWorkspaceId = herdr?.workspaceId,
        gentleMode = gentleMode,
        redacted = redacted,
        spawnedByHost = spawnedByHost,
        theme = theme,
        todoCompleted = todoCompleted,
        todoTotal = todoTotal,
        activeTask = activeTask,
    )
}

fun AgentEntity.toModel(openPrompts: Int = 0): AgentSummary {
    val tabMeta = parseHerdrTabMeta(herdrTabId)
    val effectiveStatus =
        if (status == AgentStatus.IDLE && tabMeta.isDone) {
            AgentStatus.DONE
        } else {
            status
        }
    return AgentSummary(
        agentId = agentId,
        machineId = machineId,
        machineName = machineName,
        machineContext = machineContext,
        machineColor = machineColor,
        sessionName = sessionName,
        cwd = cwd,
        gitBranch = gitBranch,
        model = model?.let { ModelInfo(id = it) },
        status = effectiveStatus,
        online = online,
        lastSeenAt = lastSeenAt,
        openPromptCount = openPrompts,
        lastMessagePreview = lastMessagePreview,
        launcher = launcher,
        herdr =
            if (herdrPaneId != null || herdrWorkspaceId != null || tabMeta.cleanTabId != null || tabMeta.orderIndex != null) {
                HerdrInfo(herdrPaneId, herdrTabId, herdrWorkspaceId)
            } else {
                null
            },
        gentleMode = gentleMode,
        redacted = redacted,
        spawnedByHost = spawnedByHost,
        theme = theme,
        todoCompleted = todoCompleted,
        todoTotal = todoTotal,
        activeTask = activeTask,
        contextTokens = tabMeta.contextTokens,
        contextWindow = tabMeta.contextWindow,
        contextPercent = tabMeta.contextPercent,
    )
}

fun Prompt.toEntity(
    relayId: String,
    now: Long,
    state: String = PromptState.OPEN,
) = PromptEntity(
    relayId = relayId,
    promptId = promptId,
    agentId = agentId,
    json = RpJson.encodeToString(Prompt.serializer(), this),
    state = state,
    dangerRank = Danger.rank(danger),
    openedAt = openedAt ?: now,
)

fun PromptEntity.toModel(): Prompt = RpJson.decodeFromString(Prompt.serializer(), json)

/** Agente listo para pintar: resumen + contador de prompts abiertos. */
data class AgentUi(
    val agent: AgentSummary,
) {
    val id get() = agent.agentId
}
