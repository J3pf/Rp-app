package dev.j3pf.rp.data.repo

import android.content.Context
import android.os.Build
import androidx.core.net.toUri
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.j3pf.rp.client.session.SessionPlatform
import dev.j3pf.rp.client.session.SessionPolicy
import dev.j3pf.rp.data.db.DedupDao
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.secure.SecretStore
import dev.j3pf.rp.data.secure.Session
import dev.j3pf.rp.push.DirectPushService
import dev.j3pf.rp.push.NotificationChannels
import dev.j3pf.rp.push.PushTokenProvider
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Emparejamiento (PLAN-APP §6), registro de canales de push y cierre de sesión. */
@Singleton
class SessionRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val api: RelayApi,
        private val secrets: SecretStore,
        private val settings: SettingsRepository,
        private val connection: RelayConnection,
        private val machines: MachinesRepository,
        private val agents: AgentsRepository,
        private val chat: ChatRepository,
        private val prompts: PromptsRepository,
        private val hosts: HostRepository,
        private val mesh: MeshRepository,
        private val dedup: DedupDao,
        private val tokens: PushTokenProvider,
        private val channels: NotificationChannels,
    ) {
        private val shared =
            SessionPolicy(
                normalizeUrl = ::normalizeUrl,
                healthz = api::healthz,
                pairDevice = api::pairDevice,
                putPush = api::putPush,
                revokeDevice = api::revokeDevice,
                platform =
                    object : SessionPlatform {
                        override fun currentSession(): Session? = secrets.current()

                        override fun newRelayId(): String = secrets.newRelayId()

                        override fun saveSession(session: Session) = secrets.save(session)

                        override suspend fun clearLocalSession() {
                            connection.stop()
                            WorkManager.getInstance(context).cancelAllWork()
                            clearLocalSessionData()
                            channels.removeAll()
                        }

                        override suspend fun clearCredentialsAndSettings() {
                            secrets.clear()
                            settings.wipe()
                        }

                        override suspend fun currentToken(): String? = tokens.currentToken()

                        override fun deviceName(): String = Build.MODEL ?: "Android"

                        override suspend fun directPush(): Boolean = settings.current().directPush

                        override suspend fun setDirectPush(direct: Boolean) {
                            settings.setDirectPush(direct)
                        }

                        override fun startDirectPush() = DirectPushService.start(context)

                        override fun stopDirectPush() = DirectPushService.stop(context)

                        override fun startConnection() = connection.start()
                    },
            )

        /** Health-before-pair handshake; native storage is replaced only after successful pairing. */
        suspend fun pair(
            rawUrl: String,
            rawCode: String,
        ): PairResult = shared.pair(rawUrl, rawCode)

        /** Aplica el interruptor de canal directo/FCM en el relay y arranca/detiene el servicio. */
        suspend fun applyPushChannels(direct: Boolean) = shared.applyPushChannels(direct)

        /** `onNewToken` de FCM → `PUT /v1/devices/me/push`. */
        suspend fun registerFcmToken(token: String?): Boolean = shared.registerFcmToken(token)

        /** Cerrar sesión: revoca (best-effort) el dispositivo, borra credenciales, base de datos y canales. */
        suspend fun logout() = shared.logout()

        private suspend fun clearLocalSessionData() {
            machines.clear()
            agents.clear()
            chat.clear()
            prompts.clear()
            hosts.clear()
            mesh.clear()
            dedup.clear()
        }

        companion object {
            /** Acepta `rp://pair?u=…&c=…`, URL con/sin esquema; fuerza https salvo emulador de depuración. */
            fun normalizeUrl(raw: String): String? {
                var u = raw.trim().trimEnd('/')
                if (u.isEmpty()) return null
                if (!u.contains("://")) u = "https://$u"
                val parsed = u.toHttpUrlOrNull() ?: return null
                if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty() || parsed.query != null || parsed.fragment != null) {
                    return null
                }
                val host = parsed.host.lowercase()
                if (host.isBlank() || host.startsWith(".") || host.endsWith(".")) return null
                val ok =
                    when (parsed.scheme.lowercase()) {
                        "https" -> true
                        "http" -> host == "localhost" || host == "10.0.2.2" || host == "127.0.0.1"
                        else -> false
                    }
                return if (ok) parsed.toString().trimEnd('/') else null
            }

            /** Extrae (url, code) de un `rp://pair?u=…&c=…`. */
            fun parsePairingUri(text: String): Pair<String, String>? {
                val t = text.trim()
                if (!t.startsWith("rp://pair")) return null
                val uri = t.toUri()
                val u = uri.getQueryParameter("u") ?: return null
                val c = uri.getQueryParameter("c") ?: return null
                return u to c
            }
        }
    }
