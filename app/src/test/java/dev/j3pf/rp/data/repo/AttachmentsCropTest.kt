package dev.j3pf.rp.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.FakeSecretStore
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.testClient
import dev.j3pf.rp.ui.chat.NormalizedCropRect
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AttachmentsCropTest {
    private fun writeTestPng(
        file: File,
        width: Int,
        height: Int,
    ) {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } finally {
            bmp.recycle()
        }
    }

    @Test
    fun prepareWithCropRectProducesCroppedJpegDimensions() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val repo = AttachmentsRepository(context, RelayApi(testClient(), FakeSecretStore(context, null)))
            val src = File(context.cacheDir, "crop-source.png")
            writeTestPng(src, width = 400, height = 200)

            val res =
                repo.prepare(
                    uri = Uri.fromFile(src),
                    compressImages = true,
                    cropRect = NormalizedCropRect(0.25f, 0.25f, 0.75f, 0.75f),
                )
            assertTrue(res.isSuccess)
            val staged = res.getOrThrow()
            assertEquals("image/jpeg", staged.mime)
            assertTrue(staged.file.exists())

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(staged.file.path, bounds)
            assertEquals(200, bounds.outWidth)
            assertEquals(100, bounds.outHeight)

            // Re-cropping with displayName preserves the clean filename without ULID stacking
            val res2 =
                repo.prepare(
                    uri = Uri.fromFile(staged.file),
                    compressImages = true,
                    cropRect = NormalizedCropRect(0f, 0f, 0.5f, 1f),
                    displayName = staged.name,
                )
            assertTrue(res2.isSuccess)
            val staged2 = res2.getOrThrow()
            assertEquals("crop-source.jpg", staged2.name)
            BitmapFactory.decodeFile(staged2.file.path, bounds)
            assertEquals(100, bounds.outWidth)
            assertEquals(100, bounds.outHeight)
        }

    @Test
    fun decodePreviewBitmapAndDiscardTempCameraUriWorkSafely() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val repo = AttachmentsRepository(context, RelayApi(testClient(), FakeSecretStore(context, null)))
            val camDir = File(context.cacheDir, "camera").apply { mkdirs() }
            val camFile = File(camDir, "IMG_test.jpg")
            writeTestPng(camFile, width = 300, height = 150)

            val preview = repo.decodePreviewBitmap(Uri.fromFile(camFile), maxDim = 200)
            assertNotNull(preview)
            assertTrue(preview.width <= 200 && preview.height <= 200)
            preview.recycle()

            assertTrue(camFile.exists())
            repo.discardTempCameraUri(Uri.fromFile(camFile))
            assertFalse(camFile.exists())
        }
}
