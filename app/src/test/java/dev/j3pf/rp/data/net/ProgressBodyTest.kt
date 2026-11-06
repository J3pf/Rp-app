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
 * Caracterización de [ProgressBody] (subida de blobs, RelayApi.uploadBlob): los avisos de progreso salta
 * n como máximo cada `total/100` (coercionado a >= 8192 B) y el último informe marca el total exacto.
 * Código de producción SIN cambios (Gate A, S5/S15).
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

    @Test
    fun `progreso informa en pasos de 8192 y cierra con el total exacto`() {
        val total = 20_000L
        val reports = mutableListOf<Pair<Long, Long>>()
        val body = ProgressBody(FixedBody(bodyData)) { sent, t -> reports += sent to t }

        val sink = Buffer()
        body.writeTo(sink)

        assertEquals(total, sink.size, "el cuerpo completo debe llegar al destino")
        // Caracterización: okio escribe en bloques de 16 KiB y el umbral (>=8192 de avance) no dispara
        // un informe dentro del bloque que lo cruza: 20000 B -> informes en 16384 y 20000.
        assertEquals(
            listOf(16384L to 20000L, 20000L to 20000L),
            reports,
            "avances >= 8192 B y último informe (total,total)",
        )
        assertTrue(reports.last().second == 20000L, "el último informe es (total,total)")
    }

    @Test
    fun `cuerpos pequeños informan solo al terminar`() {
        val reports = mutableListOf<Pair<Long, Long>>()
        val body = ProgressBody(FixedBody("y".repeat(4096).encodeUtf8())) { sent, t -> reports += sent to t }
        val sink = Buffer()
        body.writeTo(sink)
        assertEquals(4096L, sink.size)
        assertTrue(reports.last() == (4096L to 4096L), "el último informe es (total,total)")
        assertTrue(reports.all { it.first % 4096L == 0L }, "sin pasos intermedios antes del total (step >= 8192 no alcanzó antes)")
    }
}
