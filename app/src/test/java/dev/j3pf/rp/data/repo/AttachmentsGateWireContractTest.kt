package dev.j3pf.rp.data.repo

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.ports.AttachmentsGate
import dev.j3pf.rp.protocol.BlobInfo
import dev.j3pf.rp.testClient
import dev.j3pf.rp.testSession
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Wire-level contract proof for Unit P6 (`AttachmentsGate`): exercises the real
 * `AttachmentsRepository` exclusively through a port-typed `AttachmentsGate` reference
 * over a real HTTP server + local staging filesystem.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AttachmentsGateWireContractTest {
    private lateinit var context: Application
    private lateinit var server: MockWebServer
    private lateinit var gate: AttachmentsGate

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        server = MockWebServer().also { it.start() }
        val secrets = FakeSecretStore(context, testSession(server.url("/").toString()))
        val api = RelayApi(testClient(), secrets)
        gate = AttachmentsRepository(context, api)
    }

    @After
    fun tearDown() {
        if (::server.isInitialized) runCatching { server.shutdown() }
    }

    private fun tempFileUri(
        name: String = "note.txt",
        bytes: ByteArray = "hello-rp".encodeToByteArray(),
    ): String {
        val f = File(context.cacheDir, name).apply { writeBytes(bytes) }
        return Uri.fromFile(f).toString()
    }

    @Test
    fun prepareUploadAndDiscardRoundTripThroughPort() = runBlocking {
        val staged = gate.prepare(tempFileUri(), compressImages = false).getOrThrow()
        assertTrue(File(staged.stagingPath).exists())
        assertEquals(8L, staged.size)

        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"blobId":"blob-99","size":8}"""))
        val progress = mutableListOf<Float>()
        val result = gate.upload(staged) { progress += it }
        assertEquals<ApiResult<BlobInfo>>(ApiResult.Ok(BlobInfo(blobId = "blob-99", size = 8L)), result)
        assertTrue(progress.isNotEmpty() && progress.last() == 1.0f)

        val req = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("POST", req.method)
        assertEquals("/v1/blobs", req.path)
        assertTrue(req.body.readUtf8().contains("hello-rp"))

        gate.discard(staged)
        assertFalse(File(staged.stagingPath).exists())
    }

    @Test
    fun uploadMaps413AndNetworkFailuresAndCleanupPrunesStaleStagingThroughPort() = runBlocking {
        val staged = gate.prepare(tempFileUri("big.bin"), compressImages = false).getOrThrow()

        server.enqueue(MockResponse().setResponseCode(413).setBody("{}"))
        val http413 = gate.upload(staged)
        assertTrue(http413 is ApiResult.Http && http413.code == 413)

        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val netErr = gate.upload(staged)
        assertTrue(netErr is ApiResult.Network)

        val attachmentsDir = File(context.cacheDir, "attachments").apply { mkdirs() }
        val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
        val now = System.currentTimeMillis()
        val oldAttachment = File(attachmentsDir, "old-p6.jpg").apply { writeText("x"); setLastModified(now - 25 * 3_600_000L) }
        val freshAttachment = File(attachmentsDir, "fresh-p6.jpg").apply { writeText("x"); setLastModified(now - 1 * 3_600_000L) }
        val oldCamera = File(cameraDir, "old-cam-p6.jpg").apply { writeText("x"); setLastModified(now - 25 * 3_600_000L) }

        gate.cleanup()
        assertFalse(oldAttachment.exists())
        assertFalse(oldCamera.exists())
        assertTrue(freshAttachment.exists())
    }
}
