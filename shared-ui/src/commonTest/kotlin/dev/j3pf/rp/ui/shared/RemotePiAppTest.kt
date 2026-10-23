package dev.j3pf.rp.ui.shared

import androidx.compose.ui.graphics.Color
import dev.j3pf.rp.data.repo.AppSettings
import dev.j3pf.rp.ui.components.ALL_MACHINES
import dev.j3pf.rp.ui.util.humanSize
import dev.j3pf.rp.ui.util.lastSegment
import dev.j3pf.rp.ui.util.parseColor
import kotlin.test.Test
import kotlin.test.assertEquals

class RemotePiAppTest {
    @Test
    fun testSharedUiFoundation() {
        assertEquals(AppSettings.ALL, ALL_MACHINES)
        assertEquals("512 B", humanSize(512))
        assertEquals("1.5 KB", humanSize(1536))
        assertEquals("2.0 MB", humanSize(2L * 1024L * 1024L))
        assertEquals("repo", lastSegment("/home/user/repo/"))
        val fallback = Color(0xFF123456)
        assertEquals(fallback, parseColor("not-a-hex", fallback = fallback))
    }
}
