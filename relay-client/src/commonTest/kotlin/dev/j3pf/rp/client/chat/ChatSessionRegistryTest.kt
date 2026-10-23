package dev.j3pf.rp.client.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ChatSessionRegistryTest {
    private class FakeSession(
        val id: String,
    ) {
        var stopped = false
    }

    @Test
    fun openIncrementsRefsAndReturnsSameSession() {
        val registry =
            ChatSessionRegistry<FakeSession>(
                create = { FakeSession(it) },
                onStop = { it.stopped = true },
            )

        val s1 = registry.open("agent-1")
        val s2 = registry.open("agent-1")

        assertSame(s1, s2, "Subsequent open must return identical session instance")
        assertEquals(mapOf("agent-1" to 2), registry.activeSessions())
    }

    @Test
    fun releaseDecrementsRefsAndOnlyStopsOnZeroRefs() {
        val registry =
            ChatSessionRegistry<FakeSession>(
                create = { FakeSession(it) },
                onStop = { it.stopped = true },
            )

        val s = registry.open("agent-1")
        registry.open("agent-1")

        val released1 = registry.release("agent-1")
        assertNull(released1, "Must not stop when refs > 0")
        assertEquals(false, s.stopped)
        assertEquals(mapOf("agent-1" to 1), registry.activeSessions())

        val released2 = registry.release("agent-1")
        assertSame(s, released2, "Must return and stop when refs reaches 0")
        assertEquals(true, s.stopped)
        assertEquals(emptyMap(), registry.activeSessions())
    }

    @Test
    fun subsequentOpenAfterZeroRefsCreatesNewSession() {
        val registry =
            ChatSessionRegistry<FakeSession>(
                create = { FakeSession(it) },
                onStop = { it.stopped = true },
            )

        val s1 = registry.open("agent-1")
        registry.release("agent-1")
        assertEquals(true, s1.stopped)

        val s2 = registry.open("agent-1")
        assertEquals(false, s2.stopped)
        assertEquals(false, s1 === s2, "Must create fresh session instance after previous was stopped")
    }

    @Test
    fun clearStopsAllActiveSessionsAndEmptiesRegistry() {
        val registry =
            ChatSessionRegistry<FakeSession>(
                create = { FakeSession(it) },
                onStop = { it.stopped = true },
            )

        val s1 = registry.open("agent-1")
        val s2 = registry.open("agent-2")

        val stopped = registry.clear()
        assertEquals(2, stopped.size)
        assertEquals(true, s1.stopped)
        assertEquals(true, s2.stopped)
        assertEquals(emptyMap(), registry.activeSessions())
    }
}
