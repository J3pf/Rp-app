package dev.j3pf.rp.data.net

import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.BufferedSink
import okhttp3.RequestBody
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verification of [ProgressBody] (blob upload, RelayApi.uploadBlob): callbacks are throttled
 * to >=5% percentage increments or >=100 ms elapsed intervals, always emitting initial 0 and
 * final (total, total).
 */
class ProgressBodyTest {
    private class FixedBody(
        private val bytes: ByteString,
    ) : RequestBody() {
        override fun contentType() = null

        override fun contentLength() = bytes.size.toLong()

        override fun writeTo(sink: BufferedSink) {
            sink.write(bytes)
        }
    }

    private val bodyData: ByteString = "x".repeat(20_000).encodeUtf8()

    private class RepeatingBody(
        private val total: Long,
        private val onChunkWritten: () -> Unit = {},
    ) : RequestBody() {
        private val chunk = ByteArray(16_384) { (it % 127).toByte() }

        override fun contentType() = null

        override fun contentLength() = total

        override fun writeTo(sink: BufferedSink) {
            var written = 0L
            while (written < total) {
                val count = minOf(chunk.size.toLong(), total - written).toInt()
                sink.write(chunk, 0, count)
                sink.flush()
                written += count
                onChunkWritten()
            }
        }
    }

    @Test
    fun `progreso informa 0, avances de al menos 5 por ciento y cierra con el total exacto`() {
        val total = 20_000L
        val reports = mutableListOf<Pair<Long, Long>>()
        val nowMs = 1_000L
        val body = ProgressBody(FixedBody(bodyData), onProgress = { sent, t -> reports += sent to t }, clockMs = { nowMs })

        val sink = Buffer()
        body.writeTo(sink)

        assertEquals(total, sink.size, "el cuerpo completo debe llegar al destino")
        // okio writes in 16 KiB blocks: emits initial 0, 16384 (81% >= 5%), and final 20000 (100%).
        assertEquals(
            listOf(0L to 20000L, 16384L to 20000L, 20000L to 20000L),
            reports,
            "emite 0, avances >= 5% y último informe (total,total)",
        )
        assertEquals(total to total, reports.last(), "el último informe es (total,total)")
    }

    @Test
    fun `un cuerpo de 10 MiB throttlea avisos al paso del 5 por ciento en subidas rapidas`() {
        val total = 10L * 1024 * 1024
        val reports = mutableListOf<Pair<Long, Long>>()
        val fixedClock = 5_000L
        val body = ProgressBody(RepeatingBody(total), onProgress = { sent, t -> reports += sent to t }, clockMs = { fixedClock })
        val sink = Buffer()

        body.writeTo(sink)

        assertEquals(total, sink.size, "el cuerpo completo debe llegar al destino")
        assertEquals(0L to total, reports.first(), "siempre emite 0 al inicio")
        assertEquals(total to total, reports.last(), "el último informe debe ser (total,total)")
        assertTrue(reports.all { it.second == total }, "cada callback conserva el total declarado")
        // 0%, 5%, 10%, ..., 95%, 100% -> at most 21 reports (vs ~101 previously)
        assertTrue(reports.size in 2..22, "expected <= 22 throttled callbacks, got ${reports.size}")
        val intermediatePcts = reports.dropLast(1).map { ((it.first * 100L) / total).toInt() }
        assertTrue(
            intermediatePcts.zipWithNext().all { (prev, next) -> next - prev >= ProgressBody.MIN_PERCENT_DELTA },
            "intermediate percentage steps must be >= ${ProgressBody.MIN_PERCENT_DELTA}%: $intermediatePcts",
        )
    }

    @Test
    fun `cuerpos pequeños informan 0 y el total exacto al terminar`() {
        val reports = mutableListOf<Pair<Long, Long>>()
        val body =
            ProgressBody(
                FixedBody("y".repeat(4096).encodeUtf8()),
                onProgress = { sent, t -> reports += sent to t },
                clockMs = { 0L },
            )
        val sink = Buffer()
        body.writeTo(sink)
        assertEquals(4096L, sink.size)
        assertEquals(listOf(0L to 4096L, 4096L to 4096L), reports)
    }
}
