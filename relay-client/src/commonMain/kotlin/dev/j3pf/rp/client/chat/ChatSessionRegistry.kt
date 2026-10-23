package dev.j3pf.rp.client.chat

import dev.j3pf.rp.client.ws.RelayLock

/** Active session reference counting policy. */
class ChatSessionRegistry<S>(
    private val create: (agentId: String) -> S,
    private val onStop: (S) -> Unit,
) {
    private val lock = RelayLock()

    private class SessionEntry<S>(
        val session: S,
        var refs: Int = 0,
    )

    private val sessions = mutableMapOf<String, SessionEntry<S>>()

    fun open(agentId: String): S =
        lock.withLock {
            val entry =
                sessions.getOrPut(agentId) {
                    SessionEntry(create(agentId), 0)
                }
            entry.refs++
            entry.session
        }

    fun release(agentId: String): S? {
        val toStop =
            lock.withLock {
                val entry = sessions[agentId] ?: return@withLock null
                entry.refs--
                if (entry.refs <= 0) {
                    sessions.remove(agentId)
                    entry.session
                } else {
                    null
                }
            }
        toStop?.let(onStop)
        return toStop
    }

    fun clear(): List<S> {
        val all =
            lock.withLock {
                val list = sessions.values.map { it.session }
                sessions.clear()
                list
            }
        all.forEach(onStop)
        return all
    }

    fun activeSessions(): Map<String, Int> =
        lock.withLock {
            sessions.mapValues { it.value.refs }
        }
}
