package dev.j3pf.rp.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.protocol.AttachmentRef
import dev.j3pf.rp.protocol.BlobInfo
import dev.j3pf.rp.protocol.Ulid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

const val MAX_ATTACHMENT_BYTES = 25L * 1024 * 1024

/** Archivo local listo para subir (copiado al caché de la app). */
data class LocalAttachment(
    val id: String,
    val name: String,
    val mime: String,
    val size: Long,
    val file: File,
    val sourceUri: Uri,
)

enum class UploadState { PREPARING, READY, UPLOADING, DONE, ERROR }

/** Estado de un adjunto en el compositor. */
data class AttachmentUi(
    val id: String,
    val name: String,
    val mime: String,
    val size: Long,
    val state: UploadState,
    val progress: Float = 0f,
    val blobId: String? = null,
    val error: String? = null,
    val previewUri: Uri? = null,
) {
    val isImage get() = mime.startsWith("image/")

    fun toRef(): AttachmentRef? = blobId?.let { AttachmentRef(it, name, mime, size) }
}

@Singleton
class AttachmentsRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val api: RelayApi,
    ) {
        private val dir get() = File(context.cacheDir, "attachments").apply { mkdirs() }
        private val cameraDir get() = File(context.cacheDir, "camera").apply { mkdirs() }
        private val compressSemaphore = Semaphore(2)

        /** Uri de `FileProvider` para que la cámara escriba la foto (`TakePicture`). */
        fun newCameraTarget(): Uri {
            val f = File(cameraDir, "IMG_${System.currentTimeMillis()}.jpg")
            return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
        }

        /**
         * Copia el contenido (SAF / Photo Picker / cámara) al caché. Las fotos se comprimen a JPEG 85 % /
         * 2048 px (PLAN-APP §4.2) salvo GIF.
         */
        suspend fun prepare(
            uri: Uri,
            compressImages: Boolean = true,
        ): Result<LocalAttachment> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val (rawName, rawMime) = describe(uri)
                    val id = Ulid.new()
                    var name = sanitize(rawName)
                    var mime = rawMime
                    val out = File(dir, "$id-$name")
                    var file = out
                    try {
                        val streamLimit = if (compressImages && mime in COMPRESSIBLE) MAX_ATTACHMENT_BYTES * 2 else MAX_ATTACHMENT_BYTES
                        val inputStream = checkNotNull(context.contentResolver.openInputStream(uri)) { "No se pudo abrir el archivo" }
                        inputStream.use { input ->
                            out.outputStream().use { output ->
                                val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                                var total = 0L
                                while (true) {
                                    val read = input.read(buf)
                                    if (read < 0) break
                                    total += read
                                    require(total <= streamLimit) { "El archivo supera 25 MB" }
                                    output.write(buf, 0, read)
                                }
                            }
                        }
                        if (compressImages && mime in COMPRESSIBLE) {
                            compressSemaphore.withPermit { compress(out) }?.let { jpg ->
                                out.delete()
                                file = jpg
                                mime = "image/jpeg"
                                name = name.substringBeforeLast('.') + ".jpg"
                            }
                        }
                        require(file.length() <= MAX_ATTACHMENT_BYTES) { "El archivo supera 25 MB" }
                        LocalAttachment(id, name, mime, file.length(), file, uri)
                    } catch (t: Throwable) {
                        out.delete()
                        if (file != out) file.delete()
                        throw t
                    }
                }
            }

        suspend fun upload(
            local: LocalAttachment,
            onProgress: (Float) -> Unit,
        ): ApiResult<BlobInfo> {
            val body = local.file.asRequestBody(local.mime.toMediaTypeOrNull())
            return api.uploadBlob(local.name, local.mime, body) { sent, total ->
                if (total > 0) onProgress((sent.toFloat() / total).coerceIn(0f, 1f))
            }
        }

        fun discard(local: LocalAttachment) {
            runCatching { local.file.delete() }
        }

        fun cleanup(olderThanMs: Long = 24 * 3600_000L) {
            val limit = System.currentTimeMillis() - olderThanMs
            listOf(dir, cameraDir).forEach { d -> d.listFiles()?.filter { it.lastModified() < limit }?.forEach { it.delete() } }
        }

        private fun describe(uri: Uri): Pair<String, String> {
            var name: String? = null
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0)
            }
            val mime =
                context.contentResolver.getType(uri)
                    ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name?.substringAfterLast('.', "")?.lowercase())
                    ?: "application/octet-stream"
            val fallbackExt = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { ".$it" } ?: ""
            return (name ?: ("archivo$fallbackExt")) to mime
        }

        private fun sanitize(name: String) = name.replace(UNSAFE_FILENAME_REGEX, "_").take(120).ifEmpty { "archivo" }

        private fun compress(src: File): File? =
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(src.path, bounds)
                if (bounds.outWidth <= 0) return null
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_DIM) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val raw = BitmapFactory.decodeFile(src.path, opts) ?: return null
                val longest = max(raw.width, raw.height)
                val rotation =
                    when (ExifInterface(src.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                val m = Matrix()
                if (longest > MAX_DIM) {
                    val sc = MAX_DIM.toFloat() / longest
                    m.postScale(sc, sc)
                }
                if (rotation != 0f) m.postRotate(rotation)
                val rotated =
                    if (!m.isIdentity) {
                        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also {
                            if (it !== raw) raw.recycle()
                        }
                    } else {
                        raw
                    }
                val out =
                    File(src.parentFile, src.nameWithoutExtension + ".jpg").let {
                        if (it ==
                            src
                        ) {
                            File(src.parentFile, "c-${it.name}")
                        } else {
                            it
                        }
                    }
                try {
                    out.outputStream().use { rotated.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                } finally {
                    rotated.recycle()
                }
                out
            }.getOrNull()

        private companion object {
            const val MAX_DIM = 2048
            val COMPRESSIBLE = setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")
            val UNSAFE_FILENAME_REGEX = Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]")
        }
    }
