@file:SuppressLint("ExifInterface") // android.media.ExifInterface is sufficient for local image orientation on minSdk 29+

package dev.j3pf.rp.data.repo

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.data.net.RelayApi
import dev.j3pf.rp.data.ports.AttachmentsGate
import dev.j3pf.rp.data.ports.StagedAttachment
import dev.j3pf.rp.protocol.AttachmentRef
import dev.j3pf.rp.protocol.BlobInfo
import dev.j3pf.rp.protocol.Ulid
import dev.j3pf.rp.ui.chat.NormalizedCropRect
import dev.j3pf.rp.ui.chat.normalizeRotationDegrees
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
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
) {
    fun toStaged(): StagedAttachment =
        StagedAttachment(
            id = id,
            name = name,
            mime = mime,
            size = size,
            stagingPath = file.absolutePath,
            sourceUri = sourceUri.toString(),
        )
}

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
    val canCrop get() = isImage

    fun toRef(): AttachmentRef? = blobId?.let { AttachmentRef(it, name, mime, size) }
}

@Singleton
class AttachmentsRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val api: RelayApi,
    ) : AttachmentsGate {
        private val dir get() = File(context.cacheDir, "attachments").apply { mkdirs() }
        private val cameraDir get() = File(context.cacheDir, "camera").apply { mkdirs() }
        private val sentDir get() = File(context.cacheDir, "sent-blobs").apply { mkdirs() }
        private val compressSemaphore = Semaphore(2)

        /** Uri de `FileProvider` para que la cámara escriba la foto (`TakePicture`). */
        fun newCameraTarget(): Uri {
            val f = File(cameraDir, "IMG_${System.currentTimeMillis()}.jpg")
            return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
        }

        /**
         * Copies content (SAF / Photo Picker / camera) into app cache. Images are compressed to JPEG 85% /
         * 2048 px (PLAN-APP §4.2) except GIF, and optionally rotated by [rotationDegrees] and cropped to [cropRect].
         */
        suspend fun prepare(
            uri: Uri,
            compressImages: Boolean = true,
            cropRect: NormalizedCropRect? = null,
            displayName: String? = null,
            rotationDegrees: Int = 0,
        ): Result<LocalAttachment> =
            withContext(Dispatchers.IO) {
                var out: File? = null
                var file: File? = null
                try {
                    val (rawName, rawMime) = describe(uri)
                    val id = Ulid.new()
                    var name = sanitize(displayName?.takeIf { it.isNotBlank() } ?: rawName)
                    var mime = rawMime
                    val activeRotation = normalizeRotationDegrees(rotationDegrees)
                    val activeCrop = cropRect?.takeUnless { it.isFullImage() }
                    val hasImageEdit = activeCrop != null || activeRotation != 0
                    val isCompressible = (compressImages && mime in COMPRESSIBLE) || (hasImageEdit && mime.startsWith("image/"))
                    val streamLimit = if (isCompressible) MAX_ATTACHMENT_BYTES * 2 else MAX_ATTACHMENT_BYTES
                    if (isCompressible) {
                        val knownSize = querySize(uri)
                        require(knownSize == null || knownSize <= streamLimit) { "El archivo supera 25 MB" }
                        val jpgName = name.substringBeforeLast('.') + ".jpg"
                        val jpgOut = File(dir, "$id-$jpgName")
                        file = jpgOut
                        val directCompressed =
                            compressSemaphore.withPermit { compressFromUri(uri, jpgOut, activeCrop, activeRotation) }
                        if (directCompressed != null) {
                            mime = "image/jpeg"
                            name = jpgName
                            require(directCompressed.length() <= MAX_ATTACHMENT_BYTES) { "El archivo supera 25 MB" }
                            coroutineContext.ensureActive()
                            return@withContext Result.success(
                                LocalAttachment(id, name, mime, directCompressed.length(), directCompressed, uri),
                            )
                        }
                        jpgOut.delete()
                        file = null
                    }
                    val initialOut = File(dir, "$id-$name")
                    out = initialOut
                    file = initialOut
                    val inputStream = checkNotNull(openUriStream(uri)) { "No se pudo abrir el archivo" }
                    inputStream.use { input ->
                        initialOut.outputStream().use { output ->
                            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                            var total = 0L
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buf)
                                if (read < 0) break
                                total += read
                                require(total <= streamLimit) { "El archivo supera 25 MB" }
                                output.write(buf, 0, read)
                            }
                        }
                    }
                    if (isCompressible) {
                        compressSemaphore.withPermit { compress(initialOut, activeCrop, activeRotation) }?.let { jpg ->
                            initialOut.delete()
                            file = jpg
                            mime = "image/jpeg"
                            name = name.substringBeforeLast('.') + ".jpg"
                        }
                    }
                    val finalFile = checkNotNull(file)
                    require(finalFile.length() <= MAX_ATTACHMENT_BYTES) { "El archivo supera 25 MB" }
                    coroutineContext.ensureActive()
                    Result.success(LocalAttachment(id, name, mime, finalFile.length(), finalFile, uri))
                } catch (e: CancellationException) {
                    out?.delete()
                    if (file != out) file?.delete()
                    throw e
                } catch (t: Throwable) {
                    out?.delete()
                    if (file != out) file?.delete()
                    Result.failure(t)
                } finally {
                    deleteTempCameraFileIfOwned(uri)
                }
            }

        /** Deletes an unattached temporary camera capture (e.g. when the user cancels preview). */
        fun discardTempCameraUri(uri: Uri) = deleteTempCameraFileIfOwned(uri)

        /** Decodes a bounded, EXIF-oriented bitmap for preview/crop or composer thumbnails on [Dispatchers.IO]. */
        suspend fun decodePreviewBitmap(
            uri: Uri,
            maxDim: Int = 1600,
            rotationDegrees: Int = 0,
        ): Bitmap? =
            withContext(Dispatchers.IO) {
                val base = decodeOrientedBitmapFromUri(uri, maxDim) ?: return@withContext null
                val rot = normalizeRotationDegrees(rotationDegrees)
                if (rot == 0) {
                    base
                } else {
                    rotateBitmap(base, rot).also { rotated ->
                        if (rotated !== base && !base.isRecycled) base.recycle()
                    }
                }
            }

        /** Saves a local copy of a sent image keyed by [blobId] so chat bubbles can render it immediately. */
        fun cacheSentImage(
            blobId: String,
            sourceFile: File,
        ) {
            if (!SAFE_BLOB_ID_RE.matches(blobId) || !sourceFile.exists()) return
            runCatching {
                val target = File(sentDir, "$blobId.jpg")
                val tmp = File(sentDir, "$blobId.tmp-${System.nanoTime()}")
                sourceFile.copyTo(tmp, overwrite = true)
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
                pruneSentCache()
            }
        }

        /** Loads a sent image bitmap from local sent cache, falling back to `GET /v1/blobs/{blobId}`. */
        suspend fun loadSentBlobBitmap(
            blobId: String,
            maxDim: Int = 512,
        ): Bitmap? =
            withContext(Dispatchers.IO) {
                if (!SAFE_BLOB_ID_RE.matches(blobId)) return@withContext null
                val cached = File(sentDir, "$blobId.jpg")
                if (cached.exists() && cached.length() > 0L) {
                    return@withContext decodeOrientedBitmapFromUri(Uri.fromFile(cached), maxDim)
                }
                when (api.downloadBlob(blobId, cached, MAX_ATTACHMENT_BYTES)) {
                    is ApiResult.Ok -> {
                        pruneSentCache()
                        decodeOrientedBitmapFromUri(Uri.fromFile(cached), maxDim)
                    }

                    else -> null
                }
            }

        private fun pruneSentCache() {
            val files = sentDir.listFiles()?.filter(File::isFile) ?: return
            if (files.size <= MAX_SENT_CACHE_FILES) return
            files
                .sortedBy { it.lastModified() }
                .take(files.size - MAX_SENT_CACHE_FILES)
                .forEach { it.delete() }
        }

        /** Deletes raw temporary camera captures under `cacheDir/camera/` (which may contain EXIF/GPS) immediately after staging. */
        private fun deleteTempCameraFileIfOwned(uri: Uri) {
            runCatching {
                val canonicalCameraDir = cameraDir.canonicalFile
                val candidate =
                    when {
                        uri.scheme == "file" -> uri.path?.let(::File)
                        uri.scheme == "content" && uri.authority == "${context.packageName}.fileprovider" ->
                            uri.lastPathSegment?.let { File(cameraDir, it) }
                        else -> null
                    } ?: return
                if (candidate.canonicalFile.parentFile == canonicalCameraDir && candidate.exists()) {
                    candidate.delete()
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

        override suspend fun prepare(
            uriString: String,
            compressImages: Boolean,
        ): Result<StagedAttachment> = prepare(Uri.parse(uriString), compressImages).map { it.toStaged() }

        override suspend fun upload(
            staged: StagedAttachment,
            onProgress: (Float) -> Unit,
        ): ApiResult<BlobInfo> {
            val file = File(staged.stagingPath)
            val body = file.asRequestBody(staged.mime.toMediaTypeOrNull())
            return api.uploadBlob(staged.name, staged.mime, body) { sent, total ->
                if (total > 0) onProgress((sent.toFloat() / total).coerceIn(0f, 1f))
            }
        }

        fun discard(local: LocalAttachment) {
            runCatching { local.file.delete() }
        }

        override fun discard(staged: StagedAttachment) {
            if (staged.stagingPath.isNotEmpty()) {
                runCatching { File(staged.stagingPath).delete() }
            }
        }

        override fun cleanup(olderThanMs: Long) {
            val now = System.currentTimeMillis()
            val limit = now - olderThanMs
            listOf(dir, cameraDir).forEach { d -> d.listFiles()?.filter { it.lastModified() < limit }?.forEach { it.delete() } }
            val sentLimit = now - max(olderThanMs, SENT_CACHE_MAX_AGE_MS)
            sentDir.listFiles()?.filter { it.lastModified() < sentLimit }?.forEach { it.delete() }
            pruneSentCache()
        }

        fun clearAll() = clearCache(context)

        private fun describe(uri: Uri): Pair<String, String> {
            var name: String? = null
            if (uri.scheme == "file") {
                name = uri.lastPathSegment
            } else {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) name = c.getString(0)
                }
                if (name.isNullOrBlank()) {
                    name = uri.lastPathSegment
                }
            }
            val ext = name?.substringAfterLast('.', "")?.lowercase()
            val mime =
                context.contentResolver.getType(uri)
                    ?: ext?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
                    ?: when (ext) {
                        "jpg", "jpeg" -> "image/jpeg"
                        "png" -> "image/png"
                        "webp" -> "image/webp"
                        "gif" -> "image/gif"
                        "heic" -> "image/heic"
                        "heif" -> "image/heif"
                        else -> null
                    }
                    ?: "application/octet-stream"
            val fallbackExt = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { ".$it" } ?: ""
            return (name ?: ("archivo$fallbackExt")) to mime
        }

        private fun sanitize(name: String) =
            name
                .replace(UNSAFE_FILENAME_REGEX, "_")
                .trimStart('.')
                .take(120)
                .ifEmpty { "archivo" }

        private fun querySize(uri: Uri): Long? =
            runCatching {
                if (uri.scheme == "file") {
                    uri.path?.let { File(it).takeIf(File::exists)?.length() }
                } else {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                        if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
                    }
                }
            }.getOrNull()?.takeIf { it >= 0L }

        private fun decodeOrientedBitmapFromUri(
            uri: Uri,
            maxDim: Int,
        ): Bitmap? {
            val fromDecoder =
                runCatching {
                    val source =
                        if (uri.scheme == "file" && uri.path != null) {
                            ImageDecoder.createSource(File(requireNotNull(uri.path)))
                        } else {
                            ImageDecoder.createSource(context.contentResolver, uri)
                        }
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val srcWidth = info.size.width
                        val srcHeight = info.size.height
                        val longest = max(srcWidth, srcHeight)
                        if (longest > maxDim && srcWidth > 0 && srcHeight > 0) {
                            val scale = maxDim.toFloat() / longest
                            val targetW = (srcWidth * scale).toInt().coerceAtLeast(1)
                            val targetH = (srcHeight * scale).toInt().coerceAtLeast(1)
                            decoder.setTargetSize(targetW, targetH)
                        }
                    }
                }.getOrNull()
            if (fromDecoder != null) return fromDecoder

            return runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                openUriStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val raw = openUriStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
                val rotation =
                    runCatching {
                        openUriStream(uri)?.use { stream ->
                            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                                else -> 0f
                            }
                        } ?: 0f
                    }.getOrDefault(0f)
                val longest = max(raw.width, raw.height)
                val m = Matrix()
                if (longest > maxDim) {
                    val sc = maxDim.toFloat() / longest
                    m.postScale(sc, sc)
                }
                if (rotation != 0f) m.postRotate(rotation)
                if (m.isIdentity) {
                    raw
                } else {
                    val transformed = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
                    if (transformed !== raw && !raw.isRecycled) raw.recycle()
                    transformed
                }
            }.getOrNull()
        }

        private fun openUriStream(uri: Uri) =
            if (uri.scheme == "file" && uri.path != null) {
                File(requireNotNull(uri.path)).takeIf(File::exists)?.inputStream()
            } else {
                context.contentResolver.openInputStream(uri)
            }

        private fun rotateBitmap(
            source: Bitmap,
            rotationDegrees: Int,
        ): Bitmap {
            val rot = normalizeRotationDegrees(rotationDegrees)
            if (rot == 0) return source
            val m = Matrix().apply { postRotate(rot.toFloat()) }
            return Bitmap.createBitmap(source, 0, 0, source.width, source.height, m, true)
        }

        private fun cropAndScale(
            source: Bitmap,
            cropRect: NormalizedCropRect?,
            maxDim: Int,
            rotationDegrees: Int = 0,
        ): Bitmap {
            var rotated: Bitmap = source
            var cropped: Bitmap = source
            try {
                rotated = rotateBitmap(source, rotationDegrees)
                cropped = rotated
                if (cropRect != null && !cropRect.isFullImage()) {
                    val px = cropRect.toPixelRect(rotated.width, rotated.height)
                    cropped = Bitmap.createBitmap(rotated, px.left, px.top, px.width, px.height)
                    if (rotated !== source && cropped !== rotated && !rotated.isRecycled) {
                        rotated.recycle()
                    }
                }
                val longest = max(cropped.width, cropped.height)
                if (longest > maxDim) {
                    val sc = maxDim.toFloat() / longest
                    val targetW = (cropped.width * sc).toInt().coerceAtLeast(1)
                    val targetH = (cropped.height * sc).toInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
                    if (cropped !== source && scaled !== cropped && !cropped.isRecycled) {
                        cropped.recycle()
                    }
                    return scaled
                }
                return cropped
            } catch (t: Throwable) {
                if (cropped !== source && cropped !== rotated && !cropped.isRecycled) {
                    cropped.recycle()
                }
                if (rotated !== source && !rotated.isRecycled) {
                    rotated.recycle()
                }
                throw t
            }
        }

        /**
         * Decodes and resizes compressible images directly from [uri] via [ImageDecoder] (API 29+),
         * handling EXIF orientation in a single native pass without a raw temp copy or a second
         * rotated bitmap allocation. Returns `null` if unavailable (e.g., Robolectric) so callers
         * can fall back cleanly to the stream + [compress] path.
         */
        private fun compressFromUri(
            uri: Uri,
            targetFile: File,
            cropRect: NormalizedCropRect? = null,
            rotationDegrees: Int = 0,
        ): File? =
            runCatching {
                val source =
                    if (uri.scheme == "file" && uri.path != null) {
                        ImageDecoder.createSource(File(requireNotNull(uri.path)))
                    } else {
                        ImageDecoder.createSource(context.contentResolver, uri)
                    }
                val decodeCap = if (cropRect != null && !cropRect.isFullImage()) MAX_CROP_DECODE_DIM else MAX_DIM
                val decoded =
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val srcWidth = info.size.width
                        val srcHeight = info.size.height
                        val longest = max(srcWidth, srcHeight)
                        if (longest > decodeCap && srcWidth > 0 && srcHeight > 0) {
                            val scale = decodeCap.toFloat() / longest
                            val targetW = (srcWidth * scale).toInt().coerceAtLeast(1)
                            val targetH = (srcHeight * scale).toInt().coerceAtLeast(1)
                            decoder.setTargetSize(targetW, targetH)
                        }
                    }
                var outputBitmap: Bitmap? = null
                try {
                    outputBitmap = cropAndScale(decoded, cropRect, MAX_DIM, rotationDegrees)
                    targetFile.outputStream().use { output ->
                        outputBitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
                    }
                    targetFile.takeIf { it.exists() && it.length() > 0L }
                } finally {
                    if (outputBitmap != null && outputBitmap !== decoded && !outputBitmap.isRecycled) {
                        outputBitmap.recycle()
                    }
                    if (!decoded.isRecycled) {
                        decoded.recycle()
                    }
                }
            }.getOrNull()

        @SuppressLint("ExifInterface") // android.media.ExifInterface is sufficient for local image orientation on minSdk 29+
        private fun compress(
            src: File,
            cropRect: NormalizedCropRect? = null,
            rotationDegrees: Int = 0,
        ): File? =
            runCatching {
                val decodeCap = if (cropRect != null && !cropRect.isFullImage()) MAX_CROP_DECODE_DIM else MAX_DIM
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(src.path, bounds)
                if (bounds.outWidth <= 0) return null
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / sample > decodeCap) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val raw = BitmapFactory.decodeFile(src.path, opts) ?: return null
                var rotated: Bitmap? = null
                var finalBitmap: Bitmap? = null
                try {
                    val rotation =
                        when (ExifInterface(src.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                            else -> 0f
                        }
                    val m = Matrix()
                    if (rotation != 0f) m.postRotate(rotation)
                    rotated =
                        if (!m.isIdentity) {
                            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
                        } else {
                            raw
                        }
                    finalBitmap = cropAndScale(rotated, cropRect, MAX_DIM, rotationDegrees)
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
                    out.outputStream().use { finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                    out
                } finally {
                    if (finalBitmap != null && finalBitmap !== rotated && finalBitmap !== raw && !finalBitmap.isRecycled) {
                        finalBitmap.recycle()
                    }
                    if (rotated != null && rotated !== raw && !rotated.isRecycled) {
                        rotated.recycle()
                    }
                    if (!raw.isRecycled) {
                        raw.recycle()
                    }
                }
            }.getOrNull()

        companion object {
            private const val MAX_DIM = 2048
            private const val MAX_CROP_DECODE_DIM = 4096
            private const val MAX_SENT_CACHE_FILES = 80
            private const val SENT_CACHE_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
            private val SAFE_BLOB_ID_RE = Regex("^[A-Za-z0-9_-]{8,64}$")
            private val COMPRESSIBLE = setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")
            private val UNSAFE_FILENAME_REGEX = Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]")

            fun clearCache(context: Context) {
                listOf(
                    File(context.cacheDir, "attachments"),
                    File(context.cacheDir, "camera"),
                    File(context.cacheDir, "sent-blobs"),
                ).forEach { d ->
                    runCatching { d.listFiles()?.forEach { it.delete() } }
                }
            }
        }
    }
