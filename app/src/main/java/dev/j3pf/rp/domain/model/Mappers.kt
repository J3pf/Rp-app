package dev.j3pf.rp.domain.model

import dev.j3pf.rp.data.db.AgentEntity
import dev.j3pf.rp.data.db.MachineEntity
import dev.j3pf.rp.data.db.PromptEntity
import dev.j3pf.rp.data.db.PromptState
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.Danger
import dev.j3pf.rp.protocol.HerdrInfo
import dev.j3pf.rp.protocol.MachineQuota
import dev.j3pf.rp.protocol.MachineSummary
import dev.j3pf.rp.protocol.ModelInfo
import dev.j3pf.rp.protocol.Prompt
import dev.j3pf.rp.protocol.RpJson
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

fun AgentSummary.toEntity(relayId: String) =
    AgentEntity(
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
        herdrTabId = herdr?.tabId,
        herdrWorkspaceId = herdr?.workspaceId,
        gentleMode = gentleMode,
        redacted = redacted,
        spawnedByHost = spawnedByHost,
    )

fun AgentEntity.toModel(openPrompts: Int = 0) =
    AgentSummary(
        agentId = agentId,
        machineId = machineId,
        machineName = machineName,
        machineContext = machineContext,
        machineColor = machineColor,
        sessionName = sessionName,
        cwd = cwd,
        gitBranch = gitBranch,
        model = model?.let { ModelInfo(id = it) },
        status = status,
        online = online,
        lastSeenAt = lastSeenAt,
        openPromptCount = openPrompts,
        lastMessagePreview = lastMessagePreview,
        launcher = launcher,
        herdr =
            if (herdrPaneId != null || herdrWorkspaceId != null || herdrTabId != null) {
                HerdrInfo(herdrPaneId, herdrTabId, herdrWorkspaceId)
            } else {
                null
            },
        gentleMode = gentleMode,
        redacted = redacted,
        spawnedByHost = spawnedByHost,
    )

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
