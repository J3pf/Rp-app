package dev.j3pf.rp.data.repo

import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.testClient
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verdict rev-3 fix (2), APP-01: `attachments.cleanup()` runs at app start (`RpApp.kt:46`) and
 * deletes stale staging files. Pinned on unchanged production code under Robolectric: two files in
 * each staging dir, one older than the default 24 h window, one fresh — only the old ones go.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AttachmentsCleanupTest {
    @Test
    fun cleanupDeletesOnlyFilesOlderThan24hInBothStagingDirs() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = AttachmentsRepository(context, RelayApi(testClient(), FakeSecretStore(context, null)))

        val attachments = File(context.cacheDir, "attachments").apply { mkdirs() }
        val camera = File(context.cacheDir, "camera").apply { mkdirs() }
        val now = System.currentTimeMillis()
        val oldAttachment = File(attachments, "old-1.jpg").apply { writeText("x"); setLastModified(now - 25 * 3_600_000L) }
        val freshAttachment = File(attachments, "fresh-1.jpg").apply { writeText("x"); setLastModified(now - 1 * 3_600_000) }
        val oldCamera = File(camera, "old-cam.jpg").apply { writeText("x"); setLastModified(now - 25 * 3_600_000L) }
        val freshCamera = File(camera, "fresh-cam.jpg").apply { writeText("x"); setLastModified(now - 1 * 3_600_000) }

        repo.cleanup()

        assertFalse(oldAttachment.exists())
        assertFalse(oldCamera.exists())
        assertTrue(freshAttachment.exists())
        assertTrue(freshCamera.exists())
    }
}
