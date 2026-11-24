package dev.j3pf.rp.push

import android.util.Log
import dev.j3pf.rp.data.db.PromptDao
import dev.j3pf.rp.data.ports.PushEnvelopeCrypto
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.protocol.Danger
import dev.j3pf.rp.protocol.PushCancelBody
import dev.j3pf.rp.protocol.PushKind
import dev.j3pf.rp.protocol.PushPromptBody
import dev.j3pf.rp.protocol.PushSettledBody
import dev.j3pf.rp.protocol.RpJson
import dev.j3pf.rp.protocol.str
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Punto único de entrada de los pushes (canal directo y FCM). Descifra FCM, deduplica y delega en
 * [NotificationFactory].
 */
@Singleton
class PushRouter
    @Inject
    constructor(
        private val factory: NotificationFactory,
        private val deduper: NotificationDeduper,
        private val pushEnvelopeCrypto: PushEnvelopeCrypto,
        private val secrets: SecretStore,
        private val settings: SettingsRepository,
        private val promptDao: PromptDao,
    ) {
        /** FCM data: `{v,k,n,c}`. Falla de descifrado → se ignora (PLAN-APP §5.2.1). */
        suspend fun handleFcm(data: Map<String, String>): Boolean {
            val k = data["k"] ?: return false
            val key = secrets.current()?.pushKey ?: return false
            val nonce = data["n"] ?: return false
            val cipher = data["c"] ?: return false
            val plain = pushEnvelopeCrypto.decrypt(key, nonce, cipher)
            if (plain == null) {
                Log.w(TAG, "FCM decrypt failed for kind=$k")
                return false
            }
            val body =
                runCatching { RpJson.parseToJsonElement(plain) as JsonObject }
                    .onFailure { Log.w(TAG, "FCM payload JSON parse failed for kind=$k", it) }
                    .getOrNull() ?: return false
            return handle(k, body, pushId = null)
        }

        /** `push.notify {k, pushId?, body}` del canal directo (ya va por TLS, sin cifrado adicional). */
        suspend fun handleDirect(
            k: String,
            body: JsonObject,
            pushId: String?,
        ): Boolean = handle(k, body, pushId)

        private suspend fun handle(
            k: String,
            body: JsonObject,
            pushId: String?,
        ): Boolean {
            return when (k) {
                PushKind.PROMPT -> {
                    val p = decode(PushPromptBody.serializer(), body) ?: return false
                    // Un replay de un prompt ya respondido no puede volver a la bandeja: ni si lo respondió
                    // este dispositivo (marca del deduper) ni si el relay lo cerró (fila resuelta).
                    if (deduper.answeredRecently(p.promptId)) return true
                    val relayId = secrets.current()?.relayId
                    if (relayId != null && promptDao.recentlyResolved(relayId, p.promptId, System.currentTimeMillis())) return true
                    if (!deduper.firstSeen(NotificationDeduper.promptKey(p.promptId))) return true
                    // Silencio por agente (local); las autorizaciones críticas pueden saltárselo si el ajuste está activo.
                    val s = settings.current()
                    val critical = s.criticalAlways && p.danger == Danger.HIGH
                    if (p.agentId in s.mutedAgents && !critical) return true
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
                    factory.cancelWorking(s.agentId)
                    // pushId o agentId+preview: un mismo "terminó" sólo se avisa una vez.
                    val key = "settled:${pushId ?: (s.agentId + ":" + (s.preview ?: "").hashCode())}"
                    if (!deduper.firstSeen(key)) return true
                    if (s.agentId in settings.current().mutedAgents) return true
                    factory.showSettled(s)
                    true
                }

                PushKind.ERROR, PushKind.TERMINAL_WAIT -> {
                    val agentId = body.str("agentId") ?: return false
                    if (k == PushKind.ERROR) {
                        factory.cancelWorking(agentId)
                    }
                    val detailOrText = body.str("detail") ?: body.str("message") ?: body.str("title") ?: ""
                    val key = "$k:${pushId ?: (agentId + ":" + detailOrText.hashCode())}"
                    if (!deduper.firstSeen(key)) return true
                    if (agentId in settings.current().mutedAgents) return true
                    factory.showSimple(k, body)
                    true
                }

                PushKind.TEST -> {
                    val dedupId = pushId ?: body.str("pushId") ?: body.str("id")
                    if (dedupId != null && !deduper.firstSeen("test:$dedupId")) return true
                    factory.showTest(body)
                    true
                }

                else -> {
                    Log.w(TAG, "Ignoring unknown push kind=$k")
                    false
                }
            }
        }

        private fun <T> decode(
            s: kotlinx.serialization.KSerializer<T>,
            body: JsonObject,
        ): T? =
            runCatching { RpJson.decodeFromJsonElement(s, body) }
                .onFailure { Log.w(TAG, "Push body decode failed for ${s.descriptor.serialName}", it) }
                .getOrNull()

        private companion object {
            const val TAG = "PushRouter"
        }
    }
