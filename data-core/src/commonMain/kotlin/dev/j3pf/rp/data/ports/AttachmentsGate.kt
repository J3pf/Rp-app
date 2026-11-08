package dev.j3pf.rp.data.ports

import dev.j3pf.rp.data.net.ApiResult
import dev.j3pf.rp.protocol.BlobInfo

const val MAX_ATTACHMENT_BYTES = 25L * 1024 * 1024

/**
 * Platform-neutral descriptor for a local attachment copied/prepared into app staging storage
 * and ready for upload via [AttachmentsGate].
 */
data class StagedAttachment(
    val id: String,
    val name: String,
    val mime: String,
    val size: Long,
    val stagingPath: String = "",
    val sourceUri: String = "",
)

/**
 * Port contract for attachment staging, upload with progress reporting, discard, and stale-cache
 * cleanup (Unit P6).
 *
 * Keeps all platform-specific URI/File types out of `:data-core` while preserving the exact
 * Android `AttachmentsRepository` upload (`RelayApi.uploadBlob`), 25 MB limit, and 24-hour
 * staging cleanup semantics.
 */
interface AttachmentsGate {
    suspend fun prepare(
        uriString: String,
        compressImages: Boolean = true,
    ): Result<StagedAttachment>

    suspend fun upload(
        staged: StagedAttachment,
        onProgress: (Float) -> Unit = {},
    ): ApiResult<BlobInfo>

    fun discard(staged: StagedAttachment)

    fun cleanup(olderThanMs: Long = 24 * 3600_000L)
}
