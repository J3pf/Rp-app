package dev.j3pf.rp.data

import dev.j3pf.rp.data.repo.SessionRepository
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionRepositoryTest {
    @Test
    fun `QR rp pair`() {
        assertEquals(
            "https://relay.example.com" to "ABCD2345",
            SessionRepository.parsePairingUri("rp://pair?u=https://relay.example.com&c=ABCD2345"),
        )
        assertNull(SessionRepository.parsePairingUri("https://otra.cosa"))
        assertNull(SessionRepository.parsePairingUri("rp://pair?u=https://x"))
    }

    @Test
    fun `normaliza la url del relay`() {
        assertEquals("https://relay.example.com", SessionRepository.normalizeUrl(" relay.example.com/ "))
        assertEquals("https://relay.example.com:8443", SessionRepository.normalizeUrl("https://relay.example.com:8443/"))
        assertEquals("http://10.0.2.2:8080", SessionRepository.normalizeUrl("http://10.0.2.2:8080"))
        assertNull(SessionRepository.normalizeUrl("http://evil.example.com"))
        assertNull(SessionRepository.normalizeUrl(""))
    }
}
