package dev.j3pf.rp.ui.chat

/** Método de entrega elegido en el compositor (PLAN-APP §3). */
enum class DeliveryChoice(
    val label: String,
) {
    AUTO("Automático"),
    TYPED("Como tecleado 🔒"),
    EXTENSION("Mensaje de extensión"),
    COMMAND("Comando /…"),
}

enum class AttachmentUploadState {
    PREPARING,
    READY,
    UPLOADING,
    DONE,
    ERROR,
}

data class ChatAttachment(
    val id: String,
    val name: String,
    val mime: String = "",
    val size: Long = 0L,
    val state: AttachmentUploadState = AttachmentUploadState.READY,
    val progress: Float = 0f,
    val error: String? = null,
) {
    val isImage get() = mime.startsWith("image/")
}
