package dev.j3pf.rp.data.secure

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterization (T1/Gate A) of the non-crypto scaffold of `SecretStore` on UNCHANGED code.
 * The Tink/AndroidKeyStore crypto path itself cannot run under Robolectric (SC-02/SC-03 gap in
 * docs/parity/o1-data.md): the S14 in-place upgrade proof for it runs on device at Gate C.
 * Here we pin what is testable without Keystore: fresh-install null session, clear(), and the
 * `newRelayId()` format contract (8 bytes as 16 lowercase hex chars, two calls differ).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecretStoreScaffoldTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun freshInstallHasNoSession() {
        val store = SecretStore(context)
        assertNull(store.current())
        assertNull(store.session.value)
    }

    @Test
    fun clearOnNothingToClearIsANullSession() {
        val store = SecretStore(context)
        store.clear()
        assertNull(store.current())
    }

    @Test
    fun newRelayIdIsEightBytesAsSixteenLowercaseHexCharsAndUniquePerCall() {
        val store = SecretStore(context)
        val a = store.newRelayId()
        val b = store.newRelayId()
        assertTrue("'$a' must be 8 bytes as 16 lowercase hex", a.matches(Regex("^[0-9a-f]{16}$")))
        assertTrue("'$b' must be 8 bytes as 16 lowercase hex", b.matches(Regex("^[0-9a-f]{16}$")))
        assertNotEquals(a, b)
    }

    @Test
    fun stateFlowExposesTheSameValueAsCurrentWithoutWrites() {
        val store = SecretStore(context)
        // Reading the public StateFlow never mutates credentials (no accidental write surface).
        assertFalse(store.session.value != null)
        assertEquals(store.current(), store.session.value)
    }
}
