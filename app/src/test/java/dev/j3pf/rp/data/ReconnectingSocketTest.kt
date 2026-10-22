package dev.j3pf.rp.data

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.data.net.ConnState
import dev.j3pf.rp.data.net.ReconnectingSocket
import dev.j3pf.rp.testClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReconnectingSocketTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
    private val server = MockWebServer().also { it.start() }
    private val opened = LinkedBlockingQueue<WebSocket>()
    private val closed = LinkedBlockingQueue<Int>()
    private var requests = 0
    private val socket =
        ReconnectingSocket(
            context,
            testClient(),
            scope,
            requestProvider = {
                requests++
                Request.Builder().url(server.url("/socket")).build()
            },
            onSocketOpen = { opened.add(it) },
            onSocketText = {},
            onSocketClosed = { closed.add(it) },
        )

    @After
    fun tearDown() {
        socket.stop()
        scope.cancel()
        server.shutdown()
    }

    private fun accept() {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onClosing(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        webSocket.close(code, reason)
                    }
                },
            ),
        )
    }

    private fun awaitOpen() {
        scheduler.runCurrent()
        assertNotNull(opened.poll(5, TimeUnit.SECONDS))
        assertEquals(ConnState.Connected, socket.state.value)
    }

    @Test
    fun availableLostAndValidatedHandoverKeepNativeOwnership() {
        val shadow = shadowOf(cm)
        val unrelated = ConnectivityManager.NetworkCallback()
        cm.registerNetworkCallback(
            android.net.NetworkRequest
                .Builder()
                .build(),
            unrelated,
        )
        accept()
        socket.start()
        socket.start()
        awaitOpen()
        val callback = shadow.networkCallbacks.single { it != unrelated }
        val first = ShadowNetwork.newInstance(101)
        val second = ShadowNetwork.newInstance(102)
        val validated = NetworkCapabilities().also { shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) }
        callback.onAvailable(first)
        callback.onCapabilitiesChanged(first, validated)
        callback.onCapabilitiesChanged(second, NetworkCapabilities())
        scheduler.runCurrent()
        assertEquals(1, requests)
        accept()
        callback.onCapabilitiesChanged(second, validated)
        awaitOpen()
        assertEquals(2, requests)
        assertEquals(1006, closed.poll(5, TimeUnit.SECONDS))
        assertNotNull(cm.activeNetwork)
        callback.onLost(first)
        assertEquals(ConnState.Connected, socket.state.value)
        callback.onLost(second)
        assertEquals(ConnState.Disconnected, socket.state.value)
        assertEquals(1006, closed.poll(5, TimeUnit.SECONDS))
        accept()
        callback.onAvailable(second)
        awaitOpen()
        socket.stop()
        assertEquals(1000, closed.poll(5, TimeUnit.SECONDS))
        assertEquals(setOf(unrelated), shadow.networkCallbacks)
        cm.unregisterNetworkCallback(unrelated)
    }

    @Test
    fun handshake401And403AreFatalAndUnregisterOnlyOwnedCallback() =
        runBlocking {
            for (status in listOf(401, 403)) {
                server.enqueue(MockResponse().setResponseCode(status))
                socket.start()
                scheduler.runCurrent()
                val fatal = withTimeout(5000) { socket.state.first { it is ConnState.Fatal } } as ConnState.Fatal
                assertEquals(4001, fatal.code)
                assertEquals("HTTP $status", fatal.reason)
                assertEquals(4001, closed.poll(5, TimeUnit.SECONDS))
                assertTrue(shadowOf(cm).networkCallbacks.isEmpty())
                socket.retryNow(force = true)
                scheduler.advanceUntilIdle()
                assertEquals(fatal, socket.state.value)
            }
            assertEquals(2, requests)
        }

    @Test
    fun stopUnregistersAndClosesWithNormalCode() {
        accept()
        socket.start()
        scheduler.runCurrent()
        socket.stop()
        assertEquals(1000, closed.poll(5, TimeUnit.SECONDS))
        assertEquals(ConnState.Disconnected, socket.state.value)
        assertTrue(shadowOf(cm).networkCallbacks.isEmpty())
    }
}
