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
