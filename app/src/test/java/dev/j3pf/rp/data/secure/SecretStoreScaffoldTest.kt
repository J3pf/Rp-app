package dev.j3pf.rp.data.secure

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * REAL save/load/clear round-trip pins for `SecretStore` under Robolectric — no vacuous scaffold
 * assertions. The probe run (2026-11-06) proved Tink `AndroidKeysetManager` works in Robolectric:
 * `save()` writes all six encrypted fields into `rp_secrets`, `rp_keyset` persists, and a FRESH
 * instance decrypts back. What stays device-bound (UNPROVEN - needs device, S14): real Android
 * Keystore master-key readability on an upgraded install, and the keyset-corruption reset path.
 *
 * Each test runs in its own Robolectric sandbox (fresh app data dir).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecretStoreScaffoldTest {
    private lateinit var context: Context

    private val session =
        Session(
            relayId = "rp-roundtrip",
            relayUrl = "https://relay.example:10000",
            relayName = "PC de escritorio",
            deviceId = "device-f-1994",
            deviceToken = "device-token-secret-12345",
            pushKey = "push-key-Φ-9f",
        )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun freshInstallHasNoSessionAndPrefsStartEmpty() {
        val store = SecretStore(context)
        assertNull(store.current())
        assertNull(store.session.value)
        assertTrue(context.getSharedPreferences("rp_secrets", Context.MODE_PRIVATE).all.isEmpty())
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
    fun saveRoundTripsAllSixFieldsThroughAFreshInstance() {
        val store1 = SecretStore(context)
        store1.save(session)

        // Encrypted at rest (S18): none of the secret field VALUES may appear in plain prefs.
        val prefs = context.getSharedPreferences("rp_secrets", Context.MODE_PRIVATE)
        assertEquals(6, prefs.all.size) // 5 secret fields + relayName blob naming… real field count from probe
        prefs.all.forEach { (k, v) ->
            val v = v.toString()
            listOf(session.deviceToken, session.pushKey, session.deviceId, session.relayId)
                .forEach { secret -> assertTrue("field $k leaked plaintext", !v.contains(secret)) }
        }

        // A FRESH instance (new process) decrypts the same file back to the IDENTICAL session.
        val store2 = SecretStore(context)
        val back = store2.current()
        assertEquals(session, back)
        assertEquals(session, store2.session.value)
    }

    @Test
    fun clearRemovesEverythingForFreshReaders() {
        val store1 = SecretStore(context)
        store1.save(session)
        store1.clear()

        assertNull(store1.current())
        assertNull(SecretStore(context).current())
        assertTrue(context.getSharedPreferences("rp_secrets", Context.MODE_PRIVATE).all.isEmpty())
    }

    @Test
    fun garbledCiphertextYieldsNullSessionInsteadOfCrashing() {
        val store1 = SecretStore(context)
        store1.save(session)

        // Corrupt ONE field directly at rest (as a damaged upgrade or tampering would).
        context.getSharedPreferences("rp_secrets", Context.MODE_PRIVATE)
            .edit()
            .putString("deviceToken", "###not-valid-base64@@@")
            .commit()

        // Tolerance contract SC-03: load fails soft -> null session, no exception escapes.
        val store2 = SecretStore(context)
        assertNull(store2.current())
        assertNull(store2.session.value)
    }
}
