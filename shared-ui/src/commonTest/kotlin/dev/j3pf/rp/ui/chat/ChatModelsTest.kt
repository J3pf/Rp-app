package dev.j3pf.rp.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatModelsTest {
    @Test
    fun deliveryChoiceLabels() {
        assertEquals("Automático", DeliveryChoice.AUTO.label)
        assertEquals("Como tecleado 🔒", DeliveryChoice.TYPED.label)
        assertEquals("Mensaje de extensión", DeliveryChoice.EXTENSION.label)
        assertEquals("Comando /…", DeliveryChoice.COMMAND.label)
    }

    @Test
    fun chatAttachmentProperties() {
        val imageAttachment =
            ChatAttachment(
                id = "att-1",
                name = "screenshot.png",
                mime = "image/png",
                size = 1024L,
            )
        assertTrue(imageAttachment.isImage)
        assertEquals(AttachmentUploadState.READY, imageAttachment.state)
        assertEquals(0f, imageAttachment.progress)
        assertNull(imageAttachment.error)

        val textAttachment =
            ChatAttachment(
                id = "att-2",
                name = "notes.txt",
                mime = "text/plain",
                size = 512L,
                state = AttachmentUploadState.DONE,
            )
        assertFalse(textAttachment.isImage)
        assertEquals(AttachmentUploadState.DONE, textAttachment.state)
    }
}
