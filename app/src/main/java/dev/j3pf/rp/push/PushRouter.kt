package dev.j3pf.rp.push

import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.protocol.PushCancelBody
import dev.j3pf.rp.protocol.PushCrypto
import dev.j3pf.rp.protocol.PushKind
import dev.j3pf.rp.protocol.PushPromptBody
import dev.j3pf.rp.protocol.PushSettledBody
import dev.j3pf.rp.protocol.RpJson
import dev.j3pf.rp.protocol.obj
import dev.j3pf.rp.protocol.str
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject

/**
 * Punto único de entrada de los pushes (canal directo y FCM). Descifra FCM, deduplica y delega en
 * [NotificationFactory].
 */
@Singleton
class PushRouter @Inject constructor(
    private val factory: NotificationFactory,
    private val deduper: NotificationDeduper,
    private val secrets: SecretStore,
) {
    /** FCM data: `{v,k,n,c}`. Falla de descifrado → se ignora (PLAN-APP §5.2.1). */
    suspend fun handleFcm(data: Map<String, String>): Boolean {
        val k = data["k"] ?: return false
        val key = secrets.current()?.pushKey ?: return false
        val plain = PushCrypto.decrypt(key, data["n"] ?: return false, data["c"] ?: return false) ?: return false
        val body = runCatching { RpJson.parseToJsonElement(plain) as JsonObject }.getOrNull() ?: return false
        return handle(k, body, pushId = null)
    }

    /** `push.notify {k, pushId?, body}` del canal directo (ya va por TLS, sin cifrado adicional). */
    suspend fun handleDirect(k: String, body: JsonObject, pushId: String?): Boolean = handle(k, body, pushId)

    private suspend fun handle(k: String, body: JsonObject, pushId: String?): Boolean {
        return when (k) {
            PushKind.PROMPT -> {
                val p = decode(PushPromptBody.serializer(), body) ?: return false
                if (!deduper.firstSeen("prompt:${p.promptId}")) return true
                factory.showPrompt(p)
                true
            }
            PushKind.CANCEL -> {
                val c = decode(PushCancelBody.serializer(), body) ?: return false
                factory.cancelPrompt(c.promptId)
                true
            }
            PushKind.SETTLED -> {
                val s = decode(PushSettledBody.serializer(), body) ?: return false
                // pushId o agentId+preview: un mismo "terminó" sólo se avisa una vez.
                val key = "settled:${pushId ?: (s.agentId + ":" + (s.preview ?: "").hashCode())}"
                if (!deduper.firstSeen(key)) return true
                factory.showSettled(s)
                true
            }
            PushKind.ERROR, PushKind.TERMINAL_WAIT -> {
                val agentId = body.str("agentId") ?: return false
                val key = "$k:${pushId ?: (agentId + ":" + (body.str("title") ?: body.str("message") ?: "").hashCode())}"
                if (!deduper.firstSeen(key)) return true
                factory.showSimple(k, body)
                true
            }
            else -> false
        }
    }

    private fun <T> decode(s: kotlinx.serialization.KSerializer<T>, body: JsonObject): T? =
        runCatching { RpJson.decodeFromJsonElement(s, body) }.getOrNull()

    @Suppress("unused")
    private fun JsonObject.nested(key: String) = this[key].obj()
}
