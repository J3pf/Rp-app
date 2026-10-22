package dev.j3pf.rp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

const val PROTOCOL_VERSION = 1

/** Códigos de cierre propios del WebSocket (PROTOCOLO §1). */
object CloseCodes {
    const val SERVICE_RESTART = 1012
    const val INVALID_TOKEN = 4001
    const val INCOMPATIBLE_VERSION = 4002
    const val REPLACED = 4003
    const val INVALID_FRAME = 4008

    /** Cierres tras los cuales no se debe reconectar. */
    fun isFatal(code: Int) = code == INVALID_TOKEN || code == INCOMPATIBLE_VERSION
}

/** Sobre común a todos los frames WS (PROTOCOLO §2). */
@Serializable
data class Envelope(
    val v: Int = PROTOCOL_VERSION,
    val t: String,
    val id: String? = null,
    val re: String? = null,
    @Serializable(with = LenientLongSerializer::class)
    val ts: Long? = null,
    val d: JsonElement = JsonObject(emptyMap()),
) {
    fun encode(): String = RpJson.encodeToString(serializer(), this)

    inline fun <reified T> body(): T = RpJson.decodeFromJsonElement(d)

    companion object {
        fun parse(text: String): Envelope = RpJson.decodeFromString(serializer(), text)

        fun parseOrNull(text: String): Envelope? = runCatching { parse(text) }.getOrNull()

        inline fun <reified T> of(
            type: String,
            body: T,
            id: String? = null,
            re: String? = null,
            ts: Long = System.currentTimeMillis(),
        ): Envelope = Envelope(t = type, id = id, re = re, ts = ts, d = RpJson.encodeToJsonElement(body))

        fun empty(
            type: String,
            id: String? = null,
        ) = Envelope(t = type, id = id, ts = System.currentTimeMillis())
    }
}

/** Cuerpo de un `error`. */
@Serializable
data class ErrorBody(
    val code: String,
    val message: String = "",
)

object ErrorCodes {
    const val BAD_REQUEST = "bad_request"
    const val UNAUTHORIZED = "unauthorized"
    const val NOT_FOUND = "not_found"
    const val CONFLICT = "conflict"
    const val AGENT_OFFLINE = "agent_offline"
    const val TOO_LARGE = "too_large"
    const val RATE_LIMITED = "rate_limited"
    const val INTERNAL = "internal"
}

/** Tipos de mensaje (`t`). */
object MsgType {
    // App → relay
    const val APP_HELLO = "app.hello"
    const val AGENT_SUBSCRIBE = "agent.subscribe"
    const val AGENT_UNSUBSCRIBE = "agent.unsubscribe"
    const val USER_MESSAGE = "user.message"
    const val AGENT_ABORT = "agent.abort"
    const val PROMPT_ANSWER = "prompt.answer"
    const val AGENT_FORGET = "agent.forget"
    const val PUSH_ACK = "push.ack"
    const val HOST_LIST_DIRS = "host.list_dirs"
    const val HOST_HERDR_LAYOUT = "host.herdr_layout"
    const val HOST_SPAWN = "host.spawn"
    const val HOST_KILL = "host.kill"
    const val HOST_GIT_DIFF = "host.git_diff"
    const val HOST_GIT_DISCARD = "host.git_discard"
    const val MESH_SUBSCRIBE = "mesh.subscribe"
    const val MESH_UNSUBSCRIBE = "mesh.unsubscribe"

    // Relay → app
    const val APP_WELCOME = "app.welcome"
    const val AGENT_HISTORY = "agent.history"
    const val AGENT_EVENT = "agent.event"
    const val MACHINES_UPDATE = "machines.update"
    const val AGENTS_UPDATE = "agents.update"
    const val PROMPT_OPENED = "prompt.opened"
    const val PROMPT_RESOLVED = "prompt.resolved"
    const val PROMPT_ANSWER_RESULT = "prompt.answer.result"
    const val USER_MESSAGE_ACK = "user.message.ack"
    const val TERMINAL_WAIT = "terminal_wait"
    const val PUSH_NOTIFY = "push.notify"
    const val HOSTS_UPDATE = "hosts.update"
    const val HOST_DIRS = "host.dirs"
    const val HOST_HERDR_LAYOUT_RESULT = "host.herdr_layout.result"
    const val HOST_SPAWNED = "host.spawned"
    const val HOST_GIT_DIFF_RESULT = "host.git_diff.result"
    const val HOST_GIT_DISCARD_RESULT = "host.git_discard.result"
    const val MESH_SNAPSHOT = "mesh.snapshot"
    const val MESH_UPDATE = "mesh.update"
    const val MESH_COMM = "mesh.comm"
    const val ACK = "ack"
    const val ERROR = "error"
}
