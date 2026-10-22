package dev.j3pf.rp.ui.chat

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.j3pf.rp.chat.ChatState
import dev.j3pf.rp.chat.UserMsg
import dev.j3pf.rp.data.net.AnswerOutcome
import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.repo.AgentsRepository
import dev.j3pf.rp.data.repo.AttachmentUi
import dev.j3pf.rp.data.repo.AttachmentsRepository
import dev.j3pf.rp.data.repo.ChatRepository
import dev.j3pf.rp.data.repo.LocalAttachment
import dev.j3pf.rp.data.repo.PromptsRepository
import dev.j3pf.rp.data.repo.SendResult
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.data.repo.UploadState
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.Answer
import dev.j3pf.rp.protocol.Delivery
import dev.j3pf.rp.protocol.DeliveryMethod
import dev.j3pf.rp.ui.nav.ChatRoute
import dev.j3pf.rp.util.BiometricGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Método de entrega elegido en el compositor (PLAN-APP §3). */
enum class DeliveryChoice(
    val label: String,
) {
    AUTO("Automático"),
    TYPED("Como tecleado 🔒"),
    EXTENSION("Mensaje de extensión"),
    COMMAND("Comando /…"),
}

data class ChatUiState(
    val agent: AgentSummary? = null,
    val chat: ChatState = ChatState(),
    val conn: ConnState = ConnState.Disconnected,
    val attachments: List<AttachmentUi> = emptyList(),
    val choice: DeliveryChoice = DeliveryChoice.AUTO,
    /** Qué hacer si el agente está trabajando: encolar (follow‑up) o interrumpir y dirigir (steer). */
    val workingDelivery: String = Delivery.FOLLOW_UP,
    val sending: Boolean = false,
    val refreshing: Boolean = false,
    val syncingHistory: Boolean = false,
    /** Notificaciones de este agente silenciadas (preferencia local). */
    val muted: Boolean = false,
    val chatFontScale: Float = 1.0f,
    val chatMessageSpacing: String = "normal",
    /** Última entrega interactiva falló por indisponibilidad → ofrecer "Pasar a Normal". */
    val interactiveUnavailable: Boolean = false,
    val message: String? = null,
    val unreadCount: Int = 0,
) {
    val isAuto get() = agent?.gentleMode == "auto"
    val canSend get() = !sending && attachments.none { it.state != UploadState.DONE && it.state != UploadState.ERROR }
}

@HiltViewModel
class ChatViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val chats: ChatRepository,
        private val agentsRepo: AgentsRepository,
        private val settingsRepo: SettingsRepository,
        private val prompts: PromptsRepository,
        private val attachmentsRepo: AttachmentsRepository,
        connection: RelayConnection,
        val gate: BiometricGate,
    ) : ViewModel() {
        val agentId: String = savedStateHandle.toRoute<ChatRoute>().agentId
        private val session = chats.open(agentId)

        private val local = java.util.concurrent.ConcurrentHashMap<String, LocalAttachment>()
        private val extra = MutableStateFlow(ChatUiState())

        val state: StateFlow<ChatUiState> =
            combine(
                combine(session.state, session.syncing, ::Pair),
                agentsRepo.observe(agentId),
                connection.state,
                extra,
                settingsRepo.settings,
            ) { (chat, syncing), agent, conn, e, st ->
                e.copy(
                    chat = chat,
                    syncingHistory = syncing,
                    agent = agent,
                    conn = conn,
                    muted = agentId in st.mutedAgents,
                    chatFontScale = st.chatFontScale,
                    chatMessageSpacing = st.chatMessageSpacing,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

        fun setChoice(c: DeliveryChoice) = extra.update { it.copy(choice = c) }

        fun setWorkingDelivery(d: String) = extra.update { it.copy(workingDelivery = d) }

        fun consumeMessage() = extra.update { it.copy(message = null) }

        fun abort() = session.abort()

        fun refresh() {
            extra.update { it.copy(refreshing = true) }
            viewModelScope.launch {
                session.subscribe()
                kotlinx.coroutines.delay(600)
                extra.update { it.copy(refreshing = false) }
            }
        }

        fun toggleMute() {
            viewModelScope.launch { settingsRepo.setAgentMuted(agentId, !state.value.muted) }
        }

        fun setChatFontScale(scale: Float) {
            viewModelScope.launch { settingsRepo.setChatFontScale(scale) }
        }

        fun setChatMessageSpacing(spacing: String) {
            viewModelScope.launch { settingsRepo.setChatMessageSpacing(spacing) }
        }

        /** Olvida un agente desconectado (`agent.forget`). */
        fun forget(onDone: () -> Unit) {
            viewModelScope.launch {
                if (agentsRepo.forget(agentId)) onDone() else extra.update { it.copy(message = "No se pudo olvidar el agente") }
            }
        }

        /** Cierra limpiamente el agente activo enviando `/quit`. */
        fun quit(onDone: () -> Unit = {}) {
            viewModelScope.launch {
                session.send("/quit", emptyList(), Delivery.AUTO, DeliveryMethod.AUTO, asCommand = true)
                onDone()
            }
        }

        /** Reintenta el envío de un mensaje que falló. */
        fun retryUserMessage(msg: UserMsg) {
            val cId = msg.clientMsgId ?: msg.key.removePrefix("local:")
            session.removeLocal(cId)
            send(msg.text) {}
        }

        /** Reintenta la subida de un adjunto que falló. */
        fun retryAttachment(id: String) {
            val la = local[id] ?: return
            replace(id) { it.copy(state = UploadState.UPLOADING, progress = 0f, error = null) }
            viewModelScope.launch {
                when (val r = attachmentsRepo.upload(la) { p -> replace(id) { it.copy(progress = p) } }) {
                    is ApiResult.Ok -> {
                        replace(id) { it.copy(state = UploadState.DONE, progress = 1f, blobId = r.value.blobId) }
                    }

                    is ApiResult.Http -> {
                        replace(id) {
                            it.copy(
                                state = UploadState.ERROR,
                                error =
                                    if (r.code ==
                                        413
                                    ) {
                                        "Supera 25 MB"
                                    } else {
                                        "HTTP ${r.code}"
                                    },
                            )
                        }
                    }

                    is ApiResult.Network -> {
                        replace(id) { it.copy(state = UploadState.ERROR, error = "Sin conexión") }
                    }
                }
            }
        }

        /** ¿Este envío cuenta como "tecleado" y por tanto debe pedir biometría? (válida 5 min) */
        fun needsAuth(): Boolean {
            val s = state.value
            return s.choice == DeliveryChoice.TYPED || (s.choice == DeliveryChoice.AUTO && s.isAuto)
        }

        // ───────────────────────────── Adjuntos ─────────────────────────────

        fun addAttachments(uris: List<Uri>) {
            uris.forEach { uri ->
                val tempId = "pending-${System.nanoTime()}"
                extra.update {
                    it.copy(
                        attachments =
                            it.attachments +
                                AttachmentUi(tempId, uri.lastPathSegment ?: "archivo", "", 0, UploadState.PREPARING, previewUri = uri),
                    )
                }
                viewModelScope.launch {
                    val prepared = attachmentsRepo.prepare(uri)
                    val la =
                        prepared.getOrElse { e ->
                            replace(tempId) { it.copy(state = UploadState.ERROR, error = e.message ?: "No se pudo leer el archivo") }
                            return@launch
                        }
                    local[la.id] = la
                    replaceId(
                        tempId,
                        la.id,
                    ) { it.copy(id = la.id, name = la.name, mime = la.mime, size = la.size, state = UploadState.UPLOADING) }
                    when (val r = attachmentsRepo.upload(la) { p -> replace(la.id) { it.copy(progress = p) } }) {
                        is ApiResult.Ok -> {
                            replace(la.id) {
                                it.copy(
                                    state = UploadState.DONE,
                                    progress = 1f,
                                    blobId = r.value.blobId,
                                    size =
                                        r.value.size.takeIf { s -> s > 0 } ?: it.size,
                                )
                            }
                        }

                        is ApiResult.Http -> {
                            replace(la.id) {
                                it.copy(
                                    state = UploadState.ERROR,
                                    error =
                                        if (r.code ==
                                            413
                                        ) {
                                            "Supera 25 MB"
                                        } else {
                                            "HTTP ${r.code}"
                                        },
                                )
                            }
                        }

                        is ApiResult.Network -> {
                            replace(la.id) { it.copy(state = UploadState.ERROR, error = "Sin conexión") }
                        }
                    }
                }
            }
        }

        fun removeAttachment(id: String) {
            local.remove(id)?.let(attachmentsRepo::discard)
            extra.update { it.copy(attachments = it.attachments.filterNot { a -> a.id == id }) }
        }

        fun newCameraTarget(): Uri = attachmentsRepo.newCameraTarget()

        private fun replace(
            id: String,
            f: (AttachmentUi) -> AttachmentUi,
        ) = extra.update { s -> s.copy(attachments = s.attachments.map { if (it.id == id) f(it) else it }) }

        private fun replaceId(
            old: String,
            new: String,
            f: (AttachmentUi) -> AttachmentUi,
        ) = extra.update { s -> s.copy(attachments = s.attachments.map { if (it.id == old) f(it) else it }) }

        // ───────────────────────────── Envío ─────────────────────────────

        fun send(
            text: String,
            onSent: () -> Unit,
        ) {
            val s = state.value
            val body = text.trim()
            if (body.isEmpty() && s.attachments.isEmpty()) return
            if (!s.canSend) return
            val refs = s.attachments.mapNotNull { it.toRef() }
            val failed = s.attachments.count { it.state == UploadState.ERROR }
            if (failed > 0) {
                extra.update { it.copy(message = "Quita los adjuntos con error antes de enviar") }
                return
            }
            val working = s.agent?.status == "working"
            val delivery = if (working) s.workingDelivery else Delivery.AUTO
            val method =
                when (s.choice) {
                    DeliveryChoice.TYPED -> DeliveryMethod.INTERACTIVE
                    DeliveryChoice.EXTENSION -> DeliveryMethod.API
                    else -> DeliveryMethod.AUTO
                }
            val isSlash = body.startsWith("/")
            val asCommand = isSlash && (s.choice == DeliveryChoice.COMMAND || s.choice == DeliveryChoice.AUTO)
            extra.update { it.copy(sending = true, interactiveUnavailable = false) }
            viewModelScope.launch {
                val res = session.send(body, refs, delivery, method, asCommand)
                extra.update { it.copy(sending = false) }
                when (res) {
                    is SendResult.Delivered -> {
                        val err = res.ack?.error
                        val unavailable = err == "interactive_unavailable" || err == "herdr_unavailable"
                        extra.update {
                            it.copy(
                                attachments = emptyList(),
                                interactiveUnavailable = unavailable,
                                message =
                                    when (err) {
                                        "agent_blocked" -> {
                                            "El agente espera un diálogo en la terminal; el mensaje quedó en cola"
                                        }

                                        "interactive_unavailable" -> {
                                            "No se pudo teclear en la terminal: el mensaje no autoriza acciones en Auto"
                                        }

                                        "herdr_unavailable" -> {
                                            "herdr no está disponible en esa PC"
                                        }

                                        else -> {
                                            if (res.ack?.disposition ==
                                                "typed_after_dialog"
                                            ) {
                                                "Se enviará al cerrarse el diálogo"
                                            } else {
                                                null
                                            }
                                        }
                                    },
                            )
                        }
                        local.values.forEach(attachmentsRepo::discard)
                        local.clear()
                        onSent()
                    }

                    SendResult.Queued -> {
                        extra.update { it.copy(attachments = emptyList(), message = "Sin conexión: se enviará al volver la red") }
                        onSent()
                    }

                    is SendResult.Failed -> {
                        extra.update { it.copy(message = res.reason) }
                    }
                }
            }
        }

        /** "Pasar a Normal": `/gentle:mode normal` como comando (último recurso si no hay entrega interactiva). */
        fun switchToNormal() {
            viewModelScope.launch {
                val res = session.send("/gentle:mode normal", emptyList(), Delivery.AUTO, DeliveryMethod.AUTO, asCommand = true)
                extra.update {
                    it.copy(
                        interactiveUnavailable = false,
                        message = if (res is SendResult.Failed) res.reason else "Modo de permisos: Normal",
                    )
                }
            }
        }

        /** Respuesta desde una tarjeta de prompt incrustada. */
        fun answerPrompt(
            promptId: String,
            answer: Answer,
            onResult: (AnswerOutcome) -> Unit,
        ) {
            viewModelScope.launch { onResult(prompts.answer(promptId, answer)) }
        }

        override fun onCleared() {
            local.values.forEach(attachmentsRepo::discard)
            chats.release(agentId)
        }
    }
