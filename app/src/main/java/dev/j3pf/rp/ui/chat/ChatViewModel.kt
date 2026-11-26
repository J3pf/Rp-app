package dev.j3pf.rp.ui.chat

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.j3pf.rp.chat.ChatState
import dev.j3pf.rp.chat.UserMsg
import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.RelayConnection
import dev.j3pf.rp.data.ports.BiometricAuthorizer
import dev.j3pf.rp.data.ports.CloseOutcome
import dev.j3pf.rp.data.ports.HostResult
import dev.j3pf.rp.data.repo.AgentsRepository
import dev.j3pf.rp.data.repo.AttachmentUi
import dev.j3pf.rp.data.repo.AttachmentsRepository
import dev.j3pf.rp.data.repo.ChatRepository
import dev.j3pf.rp.data.repo.HostRepository
import dev.j3pf.rp.data.repo.LocalAttachment
import dev.j3pf.rp.data.repo.SendResult
import dev.j3pf.rp.data.repo.SettingsRepository
import dev.j3pf.rp.data.repo.UploadState
import dev.j3pf.rp.protocol.AgentStatus
import dev.j3pf.rp.protocol.AgentSummary
import dev.j3pf.rp.protocol.Delivery
import dev.j3pf.rp.protocol.DeliveryMethod
import dev.j3pf.rp.protocol.HostGitDiffResult
import dev.j3pf.rp.ui.nav.ChatRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

data class ChatUiState(
    val agent: AgentSummary? = null,
    val otherActiveAgents: List<AgentSummary> = emptyList(),
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
    val diffResult: HostGitDiffResult? = null,
    val loadingDiff: Boolean = false,
    val showDiffSheet: Boolean = false,
    val selectedDiffFile: String? = null,
    val showForgetConfirmation: Boolean = false,
    val readOnly: Boolean = false,
    val requireUnlockToApprove: Boolean = true,
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
        private val attachmentsRepo: AttachmentsRepository,
        private val hostRepo: HostRepository,
        connection: RelayConnection,
        val gate: BiometricAuthorizer,
    ) : ViewModel() {
        val agentId: String = savedStateHandle.toRoute<ChatRoute>().agentId
        private val session = chats.open(agentId)

        private val local = java.util.concurrent.ConcurrentHashMap<String, LocalAttachment>()
        private val attachmentJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
        private val removedAttachments = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        private val extra = MutableStateFlow(ChatUiState())
        private var forgetOnDone: ((CloseOutcome) -> Unit)? = null

        private val otherActiveAgentsFlow: StateFlow<List<AgentSummary>> =
            agentsRepo.agents
                .map { allAgents ->
                    allAgents
                        .filter {
                            it.agentId != agentId &&
                                it.online &&
                                (
                                    it.status == AgentStatus.WAITING_INPUT ||
                                        it.status == AgentStatus.WORKING ||
                                        it.status == AgentStatus.COMPACTING
                                )
                        }.sortedBy { if (it.status == AgentStatus.WAITING_INPUT) 0 else 1 }
                }.distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val state: StateFlow<ChatUiState> =
            combine(
                combine(session.state, session.syncing, ::Pair),
                combine(agentsRepo.observe(agentId), otherActiveAgentsFlow, ::Pair),
                connection.state,
                extra,
                settingsRepo.settings,
            ) { (chat, syncing), (agent, siblings), conn, e, st ->
                val hasHistoryEpoch = chat.epoch != null
                val enrichedAgent =
                    agent?.copy(
                        todoCompleted = if (hasHistoryEpoch) chat.todoCompletedCount else agent.todoCompleted,
                        todoTotal = if (hasHistoryEpoch) chat.todoTotalCount else agent.todoTotal,
                        activeTask = chat.activeTaskLabel() ?: agent.activeTask,
                        contextTokens = chat.contextTokens ?: agent.contextTokens,
                        contextWindow = chat.contextWindow ?: agent.contextWindow,
                        contextPercent = chat.contextPercent ?: agent.contextPercent,
                    )
                e.copy(
                    chat = chat,
                    syncingHistory = syncing,
                    agent = enrichedAgent,
                    otherActiveAgents = siblings,
                    conn = conn,
                    muted = agentId in st.mutedAgents,
                    chatFontScale = st.chatFontScale,
                    chatMessageSpacing = st.chatMessageSpacing,
                    requireUnlockToApprove = st.requireUnlockToApprove,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

        fun getDraft(): String = drafts[agentId].orEmpty()

        fun setDraft(text: String) {
            if (text.isBlank()) {
                drafts.remove(agentId)
            } else {
                drafts[agentId] = text
            }
        }

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

        fun openDiffSheet(targetFile: String? = null) {
            val agent = state.value.agent ?: return
            val cwd = agent.cwd ?: return
            extra.update { it.copy(showDiffSheet = true, loadingDiff = true, selectedDiffFile = targetFile) }
            viewModelScope.launch {
                when (val r = hostRepo.gitDiff(agent.machineId, cwd, targetFile)) {
                    is HostResult.Ok ->
                        extra.update {
                            val fileToSelect =
                                targetFile ?: it.selectedDiffFile ?: r.value.files
                                    .firstOrNull()
                                    ?.path
                            it.copy(diffResult = r.value, loadingDiff = false, selectedDiffFile = fileToSelect)
                        }
                    is HostResult.Failed -> extra.update { it.copy(loadingDiff = false, message = r.message) }
                }
            }
        }

        fun closeDiffSheet() {
            extra.update { it.copy(showDiffSheet = false) }
        }

        fun selectDiffFile(path: String) {
            extra.update { it.copy(selectedDiffFile = path) }
        }

        fun discardDiffFile(path: String) {
            val agent = state.value.agent ?: return
            val cwd = agent.cwd ?: return
            viewModelScope.launch {
                when (val r = hostRepo.gitDiscard(agent.machineId, cwd, path)) {
                    is HostResult.Ok -> openDiffSheet(null)
                    is HostResult.Failed -> extra.update { it.copy(message = r.message) }
                }
            }
        }

        /** Solicita confirmación antes de cerrar el agente o archivar su historial. */
        fun forget(onDone: (CloseOutcome) -> Unit) {
            forgetOnDone = onDone
            extra.update { it.copy(showForgetConfirmation = true) }
        }

        fun cancelForget() {
            forgetOnDone = null
            extra.update { it.copy(showForgetConfirmation = false) }
        }

        fun confirmForget() {
            if (!extra.value.showForgetConfirmation) return
            extra.update { it.copy(showForgetConfirmation = false) }
            viewModelScope.launch {
                when (val outcome = agentsRepo.close(agentId)) {
                    CloseOutcome.Closed, CloseOutcome.Archived -> forgetOnDone?.invoke(outcome)
                    is CloseOutcome.Failed -> extra.update { it.copy(message = outcome.message) }
                }
                forgetOnDone = null
            }
        }

        /** Cierra limpiamente el agente activo enviando `/quit`. */
        fun quit(onDone: () -> Unit = {}) {
            viewModelScope.launch {
                session.send("/quit", emptyList(), Delivery.AUTO, DeliveryMethod.AUTO, asCommand = true)
                onDone()
            }
        }

        /** Sends a terminal inspection or control command (such as `/term` or `/term enter`). */
        fun sendTerminalCommand(cmd: String) {
            viewModelScope.launch {
                val s = state.value
                if (s.isAuto && s.requireUnlockToApprove) {
                    when (gate.authenticate("Enviar comando de terminal", "Este comando actúa sobre la terminal en modo Auto")) {
                        dev.j3pf.rp.data.ports.BiometricAuthResult.OK -> {}
                        dev.j3pf.rp.data.ports.BiometricAuthResult.UNAVAILABLE -> {
                            extra.update { it.copy(message = "Configura un bloqueo de pantalla para enviar comandos de terminal") }
                            return@launch
                        }
                        dev.j3pf.rp.data.ports.BiometricAuthResult.FAILED -> {
                            extra.update { it.copy(message = "Desbloqueo cancelado") }
                            return@launch
                        }
                    }
                }
                session.send(cmd, emptyList(), Delivery.AUTO, DeliveryMethod.AUTO, asCommand = true)
            }
        }

        /** Retries sending a failed user message preserving original clientMsgId (idempotent). */
        fun retryUserMessage(msg: UserMsg) {
            val cId = msg.clientMsgId ?: msg.key.removePrefix("local:")
            viewModelScope.launch {
                session.retry(cId)
            }
        }

        /** Reintenta la subida de un adjunto que falló. */
        fun retryAttachment(id: String) {
            val la = local[id] ?: return
            removedAttachments.remove(id)
            attachmentJobs.remove(id)?.cancel()
            replace(id) { it.copy(state = UploadState.UPLOADING, progress = 0f, error = null) }
            val job =
                viewModelScope.launch(start = CoroutineStart.LAZY) {
                    val self = coroutineContext[Job]
                    try {
                        val onProgress = throttledProgressUpdater(id)
                        when (val r = attachmentsRepo.upload(la, onProgress)) {
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
                    } finally {
                        if (self != null) attachmentJobs.remove(id, self)
                    }
                }
            attachmentJobs[id] = job
            job.start()
        }

        /** ¿Este envío cuenta como "tecleado" y por tanto debe pedir biometría? (válida 5 min) */
        fun needsAuth(): Boolean {
            val s = state.value
            return s.choice == DeliveryChoice.TYPED ||
                (
                    s.requireUnlockToApprove &&
                        s.isAuto &&
                        (s.choice == DeliveryChoice.AUTO || s.choice == DeliveryChoice.COMMAND)
                )
        }

        // ───────────────────────────── Adjuntos ─────────────────────────────

        fun addAttachments(uris: List<Uri>) {
            uris.forEach { uri -> addAttachment(uri, cropRect = null) }
        }

        fun addAttachment(
            uri: Uri,
            cropRect: NormalizedCropRect? = null,
            rotationDegrees: Int = 0,
        ) {
            val tempId = "pending-${System.nanoTime()}"
            extra.update {
                it.copy(
                    attachments =
                        it.attachments +
                            AttachmentUi(tempId, uri.lastPathSegment ?: "archivo", "", 0, UploadState.PREPARING, previewUri = uri),
                )
            }
            launchPrepareAndUpload(
                slotId = tempId,
                uri = uri,
                cropRect = cropRect,
                rotationDegrees = rotationDegrees,
                displayName = null,
                oldLocal = null,
            )
        }

        fun addAttachmentWithEdit(
            uri: Uri,
            edit: ImageEditSpec,
        ) = addAttachment(uri = uri, cropRect = edit.effectiveCrop, rotationDegrees = edit.normalizedRotation)

        fun cropAttachment(
            id: String,
            cropRect: NormalizedCropRect,
        ) = editAttachment(id = id, edit = ImageEditSpec(cropRect = cropRect))

        fun editAttachment(
            id: String,
            edit: ImageEditSpec,
        ) {
            if (edit.isNoOp) return
            val existing = extra.value.attachments.firstOrNull { it.id == id } ?: return
            if (existing.state == UploadState.PREPARING) return
            val oldLocal = local.remove(id)
            val sourceUri = oldLocal?.file?.let(Uri::fromFile) ?: existing.previewUri ?: return
            attachmentJobs.remove(id)?.cancel()
            removedAttachments.remove(id)
            replace(id) {
                it.copy(state = UploadState.PREPARING, progress = 0f, blobId = null, error = null)
            }
            launchPrepareAndUpload(
                slotId = id,
                uri = sourceUri,
                cropRect = edit.effectiveCrop,
                rotationDegrees = edit.normalizedRotation,
                displayName = existing.name,
                oldLocal = oldLocal,
            )
        }

        private fun launchPrepareAndUpload(
            slotId: String,
            uri: Uri,
            cropRect: NormalizedCropRect?,
            rotationDegrees: Int,
            displayName: String?,
            oldLocal: LocalAttachment?,
        ) {
            val job =
                viewModelScope.launch(start = CoroutineStart.LAZY) {
                    val self = coroutineContext[Job]
                    var currentId = slotId
                    var oldLocalHandled = false
                    try {
                        val prepared =
                            try {
                                attachmentsRepo.prepare(
                                    uri = uri,
                                    compressImages = true,
                                    cropRect = cropRect,
                                    displayName = displayName,
                                    rotationDegrees = rotationDegrees,
                                )
                            } catch (ce: CancellationException) {
                                oldLocal?.let(attachmentsRepo::discard)
                                oldLocalHandled = true
                                throw ce
                            }
                        val la =
                            prepared.getOrElse { e ->
                                if (oldLocal != null) {
                                    oldLocalHandled = true
                                    if (slotId !in removedAttachments && attachmentJobs[slotId] === self) {
                                        local[slotId] = oldLocal
                                    } else {
                                        attachmentsRepo.discard(oldLocal)
                                    }
                                }
                                if (slotId !in removedAttachments && attachmentJobs[slotId] === self) {
                                    replace(slotId) {
                                        it.copy(state = UploadState.ERROR, error = e.message ?: "No se pudo leer el archivo")
                                    }
                                }
                                return@launch
                            }
                        if (self == null || slotId in removedAttachments || !attachmentJobs.remove(slotId, self)) {
                            attachmentsRepo.discard(la)
                            if (oldLocal != null && !oldLocalHandled) {
                                attachmentsRepo.discard(oldLocal)
                                oldLocalHandled = true
                            }
                            return@launch
                        }
                        currentId = la.id
                        attachmentJobs[la.id] = self
                        local[la.id] = la
                        if (oldLocal != null && !oldLocalHandled) {
                            attachmentsRepo.discard(oldLocal)
                            oldLocalHandled = true
                        }
                        if (la.id in removedAttachments) {
                            local.remove(la.id)?.let(attachmentsRepo::discard)
                            return@launch
                        }
                        replace(slotId) {
                            it.copy(
                                id = la.id,
                                name = la.name,
                                mime = la.mime,
                                size = la.size,
                                state = UploadState.UPLOADING,
                                progress = 0f,
                                blobId = null,
                                error = null,
                                previewUri = Uri.fromFile(la.file),
                            )
                        }
                        val onProgress = throttledProgressUpdater(la.id)
                        when (val r = attachmentsRepo.upload(la, onProgress)) {
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
                    } finally {
                        if (oldLocal != null && !oldLocalHandled) {
                            attachmentsRepo.discard(oldLocal)
                        }
                        val ownedSlot = self != null && attachmentJobs.remove(slotId, self)
                        val ownedCurrent = self != null && attachmentJobs.remove(currentId, self)
                        if (ownedSlot) removedAttachments.remove(slotId)
                        if (ownedCurrent) removedAttachments.remove(currentId)
                    }
                }
            attachmentJobs[slotId] = job
            job.start()
        }

        fun removeAttachment(id: String) {
            removedAttachments.add(id)
            attachmentJobs.remove(id)?.cancel()
            local.remove(id)?.let(attachmentsRepo::discard)
            extra.update { it.copy(attachments = it.attachments.filterNot { a -> a.id == id }) }
        }

        fun newCameraTarget(): Uri = attachmentsRepo.newCameraTarget()

        fun discardTempCameraUri(uri: Uri) = attachmentsRepo.discardTempCameraUri(uri)

        suspend fun decodePreviewBitmap(
            uri: Uri,
            maxDim: Int = 1600,
            rotationDegrees: Int = 0,
        ): Bitmap? = attachmentsRepo.decodePreviewBitmap(uri, maxDim, rotationDegrees)

        suspend fun loadSentBlobBitmap(
            blobId: String,
            maxDim: Int = 512,
        ): Bitmap? = attachmentsRepo.loadSentBlobBitmap(blobId, maxDim)

        private fun replace(
            id: String,
            f: (AttachmentUi) -> AttachmentUi,
        ) = extra.update { s -> s.copy(attachments = s.attachments.map { if (it.id == id) f(it) else it }) }

        private fun throttledProgressUpdater(id: String): (Float) -> Unit {
            var lastProgress = -1f
            var lastUpdateMs = 0L
            return { progress ->
                val nowMs = System.nanoTime() / 1_000_000L
                val delta = abs(progress - lastProgress)
                if (progress >= 1f ||
                    lastProgress < 0f ||
                    delta >= UPLOAD_PROGRESS_MIN_DELTA ||
                    nowMs - lastUpdateMs >= UPLOAD_PROGRESS_MIN_INTERVAL_MS
                ) {
                    lastProgress = progress
                    lastUpdateMs = nowMs
                    replace(id) { it.copy(progress = progress) }
                }
            }
        }

        // ───────────────────────────── Envío ─────────────────────────────

        fun send(
            text: String,
            onSent: () -> Unit,
        ) {
            val s = state.value
            val body = text.trim()
            if (body.isEmpty() && s.attachments.isEmpty()) return
            if (!s.canSend) return
            val sentAttachments = s.attachments.mapNotNull { a -> a.toRef()?.let { a.id to it } }
            val sentIds = sentAttachments.map { it.first }.toSet()
            val refs = sentAttachments.map { it.second }
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
            val imageFilesToCache =
                sentAttachments.mapNotNull { (id, ref) ->
                    if (ref.mime.startsWith("image/")) {
                        local[id]?.file?.let { file -> ref.blobId to file }
                    } else {
                        null
                    }
                }
            extra.update { it.copy(sending = true, interactiveUnavailable = false) }
            val sendDeferred =
                chats.runInRepositoryScope {
                    for ((blobId, file) in imageFilesToCache) {
                        attachmentsRepo.cacheSentImage(blobId, file)
                    }
                    session.send(body, refs, delivery, method, asCommand)
                }
            viewModelScope.launch {
                val res = sendDeferred.await()
                extra.update { it.copy(sending = false) }
                when (res) {
                    is SendResult.Delivered -> {
                        val err = res.ack?.error
                        val unavailable = err == "interactive_unavailable" || err == "herdr_unavailable"
                        extra.update {
                            it.copy(
                                attachments = it.attachments.filterNot { a -> a.id in sentIds },
                                interactiveUnavailable = unavailable,
                                message =
                                    mapDeliveryErrorMessage(err)
                                        ?: if (res.ack?.disposition == "typed_after_dialog") {
                                            "Se enviará al cerrarse el diálogo"
                                        } else {
                                            null
                                        },
                            )
                        }
                        sentIds.forEach { id -> local.remove(id)?.let(attachmentsRepo::discard) }
                        setDraft("")
                        if (isActive) onSent()
                    }

                    SendResult.Queued -> {
                        extra.update {
                            it.copy(
                                attachments = it.attachments.filterNot { a -> a.id in sentIds },
                                message = "Sin conexión: se enviará al volver la red",
                            )
                        }
                        sentIds.forEach { id -> local.remove(id)?.let(attachmentsRepo::discard) }
                        setDraft("")
                        if (isActive) onSent()
                    }

                    is SendResult.Failed -> {
                        val unavailable = res.reason == "interactive_unavailable" || res.reason == "herdr_unavailable"
                        extra.update {
                            it.copy(
                                interactiveUnavailable = unavailable,
                                message = mapDeliveryErrorMessage(res.reason) ?: res.reason,
                            )
                        }
                    }
                }
            }
        }

        private fun mapDeliveryErrorMessage(code: String?): String? =
            when (code) {
                "agent_blocked" -> "El agente espera un diálogo en la terminal; el mensaje quedó en cola"
                "interactive_unavailable" -> "No se pudo teclear en la terminal: el mensaje no autoriza acciones en Auto"
                "herdr_unavailable" -> "herdr no está disponible en esa PC"
                else -> null
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

        override fun onCleared() {
            attachmentJobs.values.forEach { it.cancel() }
            attachmentJobs.clear()
            removedAttachments.clear()
            local.values.forEach(attachmentsRepo::discard)
            chats.release(agentId)
        }

        companion object {
            private const val UPLOAD_PROGRESS_MIN_DELTA = 0.05f
            private const val UPLOAD_PROGRESS_MIN_INTERVAL_MS = 100L
            private val drafts = java.util.concurrent.ConcurrentHashMap<String, String>()

            fun clearDrafts() {
                drafts.clear()
            }
        }
    }
