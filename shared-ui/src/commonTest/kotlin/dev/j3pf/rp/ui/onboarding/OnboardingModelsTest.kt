package dev.j3pf.rp.ui.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingModelsTest {
    @Test
    fun pairUiStateDefaults() {
        val defaultState = PairUiState()
        assertFalse(defaultState.busy)
        assertNull(defaultState.error)
        assertFalse(defaultState.done)
    }

    @Test
    fun pairUiStateCustomValues() {
        val busyState = PairUiState(busy = true)
        assertTrue(busyState.busy)

        val errorState = PairUiState(error = "Invalid pairing code")
        assertEquals("Invalid pairing code", errorState.error)

        val doneState = PairUiState(done = true)
        assertTrue(doneState.done)
    }
}
