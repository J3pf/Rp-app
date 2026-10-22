package dev.j3pf.rp.client.ws

import dev.j3pf.rp.data.net.ConnState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class FakeSocketDriver : RelaySocketDriver<String, Int> {
    val listeners = mutableListOf<RelaySocketListener<Int>>()
    val requests = mutableListOf<String>()
    val closes = mutableListOf<Triple<Int, Int, String>>()
    val cancelled = mutableListOf<Int>()
    val sent = mutableListOf<Pair<Int, String>>()

    override fun connect(
        request: String,
        listener: RelaySocketListener<Int>,
    ): Int {
        requests.add(request)
        listeners.add(listener)
        return listeners.lastIndex
    }

    override fun send(
        socket: Int,
        text: String,
    ): Boolean = sent.add(socket to text)

    override fun close(
        socket: Int,
        code: Int,
        reason: String,
    ) {
        closes.add(Triple(socket, code, reason))
    }

    override fun cancel(socket: Int) {
        cancelled.add(socket)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectingSocketPolicyTest {
    @Test
    fun startStopRetryAndStaleCallbacksAreFenced() =
        runTest {
            val driver = FakeSocketDriver()
            val texts = mutableListOf<String>()
            val ended = mutableListOf<Int>()
            var registrations = 0
            val policy =
                ReconnectingSocketPolicy(
                    scope = backgroundScope,
                    driver = driver,
                    requestProvider = { "opaque request" },
                    onSocketOpen = { driver.send(it, "native hello") },
                    onSocketText = { texts.add(it) },
                    onSocketClosed = { ended.add(it) },
                    onStart = { registrations++ },
                    onStop = { registrations-- },
                )
            assertEquals(ConnState.Disconnected, policy.state.value)
            policy.retryNow()
            policy.start()
            policy.start()
            runCurrent()
            assertEquals(1, registrations)
            assertEquals(ConnState.Connecting, policy.state.value)
            policy.retryNow()
            runCurrent()
            assertEquals(1, driver.requests.size)
            driver.listeners[0].onOpen(0)
            assertTrue(policy.isConnected)
            assertEquals(listOf(0 to "native hello"), driver.sent)
            assertTrue(policy.send("payload"))
            policy.retryNow(force = true)
            runCurrent()
            assertEquals(listOf(0), driver.cancelled)
            assertEquals(listOf(1006), ended)
            driver.listeners[0].onOpen(0)
            driver.listeners[0].onText("stale")
            driver.listeners[0].onClosed(4001, "stale fatal")
            assertEquals(Triple(0, 1000, "Stale generation"), driver.closes.last())
            assertTrue(texts.isEmpty())
            driver.listeners[1].onOpen(1)
            driver.listeners[1].onText("current")
            assertEquals(listOf("current"), texts)
            policy.stop()
            assertEquals(0, registrations)
            assertEquals(ConnState.Disconnected, policy.state.value)
            assertEquals(Triple(1, 1000, "bye"), driver.closes.last())
            driver.listeners[1].onFailure(null, "stale")
            advanceTimeBy(60_000)
            assertEquals(2, driver.requests.size)
            assertFalse(policy.send("no socket"))
        }

    @Test
    fun exponentialBackoffJitterBoundariesAndOpenReset() =
        runTest {
            for (jitter in listOf(0.0, 0.5, 1.0)) {
                val driver = FakeSocketDriver()
                val policy =
                    ReconnectingSocketPolicy(
                        scope = backgroundScope,
                        driver = driver,
                        requestProvider = { "request" },
                        onSocketOpen = {},
                        onSocketText = {},
                        randomDouble = { jitter },
                    )
                policy.start()
                runCurrent()
                for (base in listOf(1000L, 2000L, 4000L, 8000L, 16000L, 30000L, 30000L)) {
                    val count = driver.requests.size
                    driver.listeners.last().onFailure(null, "offline")
                    val expected = (base * (0.8 + jitter * 0.4)).toLong()
                    advanceTimeBy(expected - 1)
                    runCurrent()
                    assertEquals(count, driver.requests.size)
                    advanceTimeBy(1)
                    runCurrent()
                    assertEquals(count + 1, driver.requests.size)
                }
                driver.listeners.last().onOpen(driver.listeners.lastIndex)
                driver.listeners.last().onClosed(1000, "normal")
                val count = driver.requests.size
                advanceTimeBy((1000 * (0.8 + jitter * 0.4)).toLong())
                runCurrent()
                assertEquals(count + 1, driver.requests.size)
                policy.stop()
            }
        }

    @Test
    fun restartImmediateFatalClosesAndHandshakeAuthenticationFailures() =
        runTest {
            for (fatal in listOf(4001, 4002, 401, 403)) {
                val driver = FakeSocketDriver()
                var stopped = 0
                val ended = mutableListOf<Int>()
                val policy =
                    ReconnectingSocketPolicy(
                        scope = backgroundScope,
                        driver = driver,
                        requestProvider = { "request" },
                        onSocketOpen = {},
                        onSocketText = {},
                        onSocketClosed = { ended.add(it) },
                        onStop = { stopped++ },
                    )
                policy.start()
                runCurrent()
                driver.listeners.last().onClosed(1012, "restart")
                runCurrent()
                assertEquals(2, driver.requests.size)
                if (fatal < 1000) {
                    driver.listeners.last().onFailure(fatal, "handshake")
                } else {
                    driver.listeners.last().onClosed(fatal, "fatal")
                }
                assertEquals(if (fatal < 1000) 4001 else fatal, (policy.state.value as ConnState.Fatal).code)
                assertEquals(1, stopped)
                policy.retryNow(force = true)
                advanceTimeBy(60_000)
                runCurrent()
                assertEquals(2, driver.requests.size)
                assertEquals(listOf(1012, if (fatal < 1000) 4001 else fatal), ended)
            }
        }

    @Test
    fun networkLossFencesSocketAndRetryIsImmediate() =
        runTest {
            val driver = FakeSocketDriver()
            val policy =
                ReconnectingSocketPolicy(
                    scope = backgroundScope,
                    driver = driver,
                    requestProvider = { "request" },
                    onSocketOpen = {},
                    onSocketText = {},
                )
            policy.start()
            runCurrent()
            driver.listeners[0].onOpen(0)
            policy.networkLost()
            assertEquals(ConnState.Disconnected, policy.state.value)
            assertEquals(listOf(0), driver.cancelled)
            driver.listeners[0].onClosed(4001, "late")
            policy.retryNow()
            runCurrent()
            assertEquals(2, driver.requests.size)
            policy.stop()
        }

    @Test
    fun nullSessionDoesNotConnectAndStopCancelsDelay() =
        runTest {
            val driver = FakeSocketDriver()
            var request: String? = null
            val policy =
                ReconnectingSocketPolicy(
                    scope = backgroundScope,
                    driver = driver,
                    requestProvider = { request },
                    onSocketOpen = {},
                    onSocketText = {},
                )
            policy.start()
            runCurrent()
            assertEquals(ConnState.Disconnected, policy.state.value)
            assertTrue(driver.requests.isEmpty())
            request = "current session"
            policy.retryNow()
            runCurrent()
            driver.listeners[0].onFailure(500, "server")
            policy.stop()
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(listOf("current session"), driver.requests)
        }
}
