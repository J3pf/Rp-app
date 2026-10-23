package dev.j3pf.rp.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatModelsTest {
    @Test
    fun deliveryChoiceLabels() {
        assertEquals("Automático", DeliveryChoice.AUTO.label)
        assertEquals("Como tecleado 🔒", DeliveryChoice.TYPED.label)
        assertEquals("Mensaje de extensión", DeliveryChoice.EXTENSION.label)
        assertEquals("Comando /…", DeliveryChoice.COMMAND.label)
    }

    @Test
    fun attachmentUploadStateEntries() {
        assertEquals(
            listOf(
                AttachmentUploadState.PREPARING,
                AttachmentUploadState.READY,
                AttachmentUploadState.UPLOADING,
                AttachmentUploadState.DONE,
                AttachmentUploadState.ERROR,
            ),
            AttachmentUploadState.entries,
        )
    }
}
