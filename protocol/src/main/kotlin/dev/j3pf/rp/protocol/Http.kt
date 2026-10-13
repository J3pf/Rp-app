package dev.j3pf.rp.protocol

import kotlinx.serialization.Serializable

@Serializable
data class PairDeviceRequest(val code: String, val name: String, val fcmToken: String? = null)

@Serializable
data class PairDeviceResponse(
    val deviceId: String,
    val deviceToken: String,
    val pushKey: String,
    val relayName: String = "",
)

@Serializable
data class PairCodeResponse(val code: String, val expiresAt: Long? = null)

@Serializable
data class PushChannelsRequest(val fcmToken: String? = null, val direct: Boolean)

@Serializable
data class MachinePatch(
    val name: String? = null,
    val context: String? = null,
    val color: String? = null,
    val icon: String? = null,
    val quietHours: QuietHours? = null,
)

@Serializable
data class QuietHours(val from: String, val to: String, val tz: String? = null)

@Serializable
data class MachinePrefs(
    val mute: Boolean = false,
    val categories: Map<String, Boolean> = emptyMap(),
    val quietHours: QuietHours? = null,
)

@Serializable
data class NotifyPrefs(
    val categories: Map<String, Boolean> = emptyMap(),
    val machines: Map<String, MachinePrefs> = emptyMap(),
)

@Serializable
data class DeviceInfo(
    val deviceId: String,
    val name: String = "",
    val createdAt: Long? = null,
    val lastSeenAt: Long? = null,
    val current: Boolean = false,
)

@Serializable
data class OkResponse(val ok: Boolean = true, val reason: String? = null)

@Serializable
data class AnswerPromptRequest(val answer: Answer)

@Serializable
data class BlobInfo(val blobId: String, val sha256: String? = null, val size: Long = 0, val mime: String? = null)

@Serializable
data class HealthResponse(val ok: Boolean = true, val version: String? = null, val uptime: Double? = null)

@Serializable
data class PromptList(val prompts: List<Prompt> = emptyList())

/** Cuerpos de los mensajes de rp-host (PROTOCOLO §9). */
@Serializable
data class HostBackends(
    val herdr: String? = null,
    val tmux: String? = null,
    val wt: Boolean = false,
    val console: Boolean = false,
)

@Serializable
data class HostInfo(
    val machineId: String,
    val hostname: String = "",
    val roots: List<String> = emptyList(),
    val online: Boolean = false,
    val backends: HostBackends? = null,
    val defaultBackend: String? = null,
    val launchers: List<String> = emptyList(),
    val defaultLauncher: String? = null,
)

@Serializable
data class HostsUpdate(val upsert: List<HostInfo> = emptyList(), val remove: List<String> = emptyList())

@Serializable
data class HostListDirs(val machineId: String, val path: String)

@Serializable
data class HostDirs(val path: String, val dirs: List<String> = emptyList())

@Serializable
data class HostMachineRef(val machineId: String)

@Serializable
data class HerdrPane(val id: String, val agent: String? = null)

@Serializable
data class HerdrTab(val id: String, val label: String = "", val panes: List<HerdrPane> = emptyList())

@Serializable
data class HerdrWorkspace(
    val id: String,
    val label: String = "",
    val cwd: String? = null,
    val tabs: List<HerdrTab> = emptyList(),
)

@Serializable
data class HerdrLayout(val workspaces: List<HerdrWorkspace> = emptyList())

@Serializable
data class HerdrTarget(
    val type: String,
    val workspaceId: String? = null,
    val paneId: String? = null,
    val direction: String? = null,
)

@Serializable
data class HostSpawn(
    val machineId: String,
    val cwd: String,
    val name: String? = null,
    val launcher: String? = null,
    val backend: String? = null,
    val herdrTarget: HerdrTarget? = null,
    val initialPrompt: String? = null,
)

@Serializable
data class HostSpawned(
    val spawnId: String,
    val backend: String? = null,
    val herdr: HerdrSpawnLoc? = null,
    val tmuxSession: String? = null,
)

@Serializable
data class HerdrSpawnLoc(val workspaceId: String? = null, val paneId: String? = null)
