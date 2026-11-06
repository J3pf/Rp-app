package dev.j3pf.rp.data.db

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S14 regression pin (suggested by the Risk item 1 verifier): the exported Room schema in
 * `:data-core` must stay IDENTICAL to the canonical `:app` export — if the two drift, the
 * migration story (same version 3, same identity hash) silently breaks.
 * Plain JVM test (org.json is not on the unit-test classpath without Robolectric; the files
 * are canonicalized by Room so a byte comparison of trimmed strings is exact).
 * Paths resolved from the :app unit-test working directory (the `app/` module dir).
 */
class SchemaCopyIdentityTest {
    private fun readBytes(p: String): String = Files.readString(Path.of(p)).trim()

    @Test
    fun dataCoreSchemaExportIsByteIdenticalToTheAppExport() {
        val app = readBytes("schemas/dev.j3pf.rp.data.db.RpDatabase/3.json")
        val core = readBytes("../data-core/schemas/dev.j3pf.rp.data.db.RpDatabase/3.json")
        assertEquals(app, core, "schema exports must not drift (S14)")
        assertTrue(app.contains("\"identityHash\": \"b4ad483aeb2f27bb4b65b4b725d2cfd7\""), app.take(300))
        assertTrue(app.contains("\"version\": 3"), app.take(300))
    }
}
