package dev.j3pf.rp.ui.theme

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.spring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {
    @Test
    fun `adaptiveSpec yields snap when reducedMotion is active`() {
        val defaultSpec = spring<Float>()
        val adaptive = RpMotion.adaptiveSpec(reducedMotion = true, defaultSpec = defaultSpec)
        assertTrue("Expected SnapSpec when reduced motion is true", adaptive is SnapSpec)
    }

    @Test
    fun `adaptiveSpec yields defaultSpec when reducedMotion is inactive`() {
        val defaultSpec = spring<Float>()
        val adaptive = RpMotion.adaptiveSpec(reducedMotion = false, defaultSpec = defaultSpec)
        assertEquals(defaultSpec, adaptive)
    }

    @Test
    fun `motion constants are properly configured`() {
        assertEquals(0.97f, RpMotion.PRESSED_SCALE, 0.001f)
        assertEquals(1.0f, RpMotion.DEFAULT_SCALE, 0.001f)
    }
}
