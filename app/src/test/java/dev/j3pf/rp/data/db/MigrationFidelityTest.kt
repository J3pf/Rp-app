package dev.j3pf.rp.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migration fidelity grid (verifier Gate-A finding 2): MIGRATION_2_3 must be pinned column-by-column
 * and table-by-table, a v3 DB produced by the CURRENT code must open in the current schema, and
 * README v0.3.3 claims (theme/TODO/activeTask on agents) must round-trip through the real AgentDao.
 * Adds on top of DatabaseMigrationTest (which stays untouched): rows DB-03a..DB-03d in docs/parity/o1-data.md.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationFidelityTest {
    private val testDb = "fidelity-test"

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            RpDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migration2To3SurvivesEveryV2AgentColumnPopulated() {
        helper.createDatabase(testDb, 2).apply {
            execSQL(
                """
                INSERT INTO agents (
                    relayId, agentId, machineId, machineName, machineContext, machineColor,
                    sessionName, cwd, gitBranch, model, status, online, lastSeenAt,
                    lastMessagePreview, launcher, herdrPaneId, herdrTabId, herdrWorkspaceId,
                    gentleMode, redacted, spawnedByHost
                ) VALUES (
                    'relay-full', 'agent-full', 'm-full', ' Laptop', 'trabajo', '#00AA55',
                    'frontend session', '/srv/web', 'feat/paginacion', 'glm-5.3', 'busy', 0, 1700112233000,
                    'preview con tildes y «comillas»', 'herdr-launcher', 'pane-77', 'tab-9', 'ws-42',
                    'gentle-cute', 1, 1
                )
                """.trimIndent(),
            )
            close()
        }

        val dbV3 = helper.runMigrationsAndValidate(testDb, 3, true, RpDatabase.MIGRATION_2_3)

        val cursor = dbV3.query("SELECT * FROM agents WHERE agentId = 'agent-full'")
        try {
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            val col: (String) -> Int = { cursor.getColumnIndexOrThrow(it) }
            assertEquals("relay-full", cursor.getString(col("relayId")))
            assertEquals("agent-full", cursor.getString(col("agentId")))
            assertEquals("m-full", cursor.getString(col("machineId")))
            assertEquals(" Laptop", cursor.getString(col("machineName")))
            assertEquals("trabajo", cursor.getString(col("machineContext")))
            assertEquals("#00AA55", cursor.getString(col("machineColor")))
            assertEquals("frontend session", cursor.getString(col("sessionName")))
            assertEquals("/srv/web", cursor.getString(col("cwd")))
            assertEquals("feat/paginacion", cursor.getString(col("gitBranch")))
            assertEquals("glm-5.3", cursor.getString(col("model")))
            assertEquals("busy", cursor.getString(col("status")))
            assertFalse(cursor.getInt(col("online")) != 0)
            assertEquals(1700112233000L, cursor.getLong(col("lastSeenAt")))
            assertEquals("preview con tildes y «comillas»", cursor.getString(col("lastMessagePreview")))
            assertEquals("herdr-launcher", cursor.getString(col("launcher")))
            assertEquals("pane-77", cursor.getString(col("herdrPaneId")))
            assertEquals("tab-9", cursor.getString(col("herdrTabId")))
            assertEquals("ws-42", cursor.getString(col("herdrWorkspaceId")))
            assertEquals("gentle-cute", cursor.getString(col("gentleMode")))
            assertTrue(cursor.getInt(col("redacted")) != 0)
            assertTrue(cursor.getInt(col("spawnedByHost")) != 0)
            // New columns receive the documented defaults for pre-existing rows:
            assertNull(cursor.getString(col("theme")))
            assertEquals(0, cursor.getInt(col("todoCompleted")))
            assertEquals(0, cursor.getInt(col("todoTotal")))
            assertNull(cursor.getString(col("activeTask")))
        } finally {
            cursor.close()
            dbV3.close()
        }
    }

    @Test
    fun migration2To3PreservesEveryOtherTable() {
        helper.createDatabase(testDb, 2).apply {
            execSQL(
                "INSERT INTO machines (relayId, machineId, name, context, color, icon, online, agentCount, openPromptCount, hostOnline, quotasJson) " +
                    "VALUES ('r1', 'm1', 'PC', 'personal', '#FFF', 'laptop', 1, 3, 2, 1, '[{\"kind\":\"cpu\",\"limit\":4}]')",
            )
            execSQL(
                "INSERT INTO prompts (relayId, promptId, agentId, json, state, dangerRank, openedAt, resolvedBy, resolvedAt) " +
                    "VALUES ('r1', 'p1', 'a1', '{\"id\":\"p1\"}', 'open', 2, 1000, NULL, NULL)",
            )
            execSQL(
                "INSERT INTO chat_records (relayId, agentId, kind, epoch, seq, json) " +
                    "VALUES ('r1', 'a1', 'event', 'e1', 5, '{\"type\":\"message_end\"}')",
            )
            execSQL(
                "INSERT INTO outbox (clientMsgId, relayId, agentId, json, attempts, createdAt) " +
                    "VALUES ('cm1', 'r1', 'a1', '{\"text\":\"hola\"}', 1, 42)",
            )
            execSQL("INSERT INTO notification_dedup (key, seenAt) VALUES ('dedup-1', 77)")
            close()
        }

        val dbV3 = helper.runMigrationsAndValidate(testDb, 3, true, RpDatabase.MIGRATION_2_3)

        run {
            val c = dbV3.query("SELECT COUNT(*) FROM machines")
            c.moveToFirst(); assertEquals(1, c.getInt(0)); c.close()
        }
        dbV3.query("SELECT name, icon, openPromptCount, hostOnline, quotasJson FROM machines WHERE machineId='m1'").use { c ->
            c.moveToFirst()
            assertEquals("PC", c.getString(0))
            assertEquals("laptop", c.getString(1))
            assertEquals(2, c.getInt(2))
            assertTrue(c.getInt(3) != 0)
            assertEquals("[{\"kind\":\"cpu\",\"limit\":4}]", c.getString(4))
        }
        dbV3.query("SELECT state, dangerRank, openedAt, resolvedBy, resolvedAt FROM prompts WHERE promptId='p1'").use { c ->
            c.moveToFirst()
            assertEquals("open", c.getString(0))
            assertEquals(2, c.getInt(1))
            assertEquals(1000L, c.getLong(2))
            assertNull(c.getString(3))
            assertNull(c.getString(4))
        }
        dbV3.query("SELECT kind, epoch, seq, json FROM chat_records WHERE agentId='a1'").use { c ->
            c.moveToFirst()
            assertEquals("event", c.getString(0))
            assertEquals("e1", c.getString(1))
            assertEquals(5L, c.getLong(2))
            assertEquals("{\"type\":\"message_end\"}", c.getString(3))
        }
        dbV3.query("SELECT json, attempts, createdAt FROM outbox WHERE clientMsgId='cm1'").use { c ->
            c.moveToFirst()
            assertEquals("{\"text\":\"hola\"}", c.getString(0))
            assertEquals(1, c.getInt(1))
            assertEquals(42L, c.getLong(2))
        }
        dbV3.query("SELECT seenAt FROM notification_dedup WHERE `key`='dedup-1'").use { c ->
            c.moveToFirst()
            assertEquals(77L, c.getLong(0))
        }
        // Row counts where the verifier asked for counts, not values:
        dbV3.query("SELECT COUNT(*) FROM prompts").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        dbV3.query("SELECT COUNT(*) FROM chat_records").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        dbV3.query("SELECT COUNT(*) FROM outbox").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        dbV3.close()
    }

    @Test
    fun v3AgentDaoRoundTripKeepsThemeTodoActiveTask() {
        // Migrate to the real v3 schema, then open through Room the same way the app does.
        helper.createDatabase(testDb, 2).apply {
            execSQL(
                "INSERT INTO agents (relayId, agentId, machineId, machineName, machineContext, machineColor, status, online, redacted, spawnedByHost) " +
                    "VALUES ('r1', 'a1', 'm1', 'PC', 'personal', '#FFF', 'idle', 1, 0, 0)",
            )
            close()
        }
        helper.runMigrationsAndValidate(testDb, 3, true, RpDatabase.MIGRATION_2_3).close()

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Open the REAL migrated v3 file (testDb, produced via MIGRATION_2_3 in this test) through Room:
        // this doubles as the S14 proof that a current-code-produced DB opens post-migration.
        // Robolectric copies the helper-produced file into the app db dir (testDb); the helper's
        // version was already closed, so Room can acquire it exclusively.
        val src = context.getDatabasePath(testDb)
        assertTrue("migrated DB must exist: $src", src.exists())
        val db = Room.databaseBuilder(context, RpDatabase::class.java, testDb).allowMainThreadQueries().build()
        try {
            val schema = db.openHelper.readableDatabase.query("PRAGMA table_info(agents)").use { c ->
                buildString { while (c.moveToNext()) append(c.getString(1)).append('(').append(c.getString(2)).append(" notnull=").append(c.getInt(3)).append(") ") }
            }
            assertTrue("agents columns include redacted: $schema", schema.contains("redacted"))
            kotlinx.coroutines.runBlocking {
            db.agents().upsert(
                listOf(
                    AgentEntity(
                        relayId = "r1",
                        agentId = "a1",
                        machineId = "m1",
                        machineName = "PC",
                        machineContext = "personal",
                        machineColor = "#FFF",
                        sessionName = null,
                        cwd = null,
                        gitBranch = null,
                        model = null,
                        status = "idle",
                        online = true,
                        lastSeenAt = null,
                        lastMessagePreview = null,
                        launcher = null,
                        herdrPaneId = null,
                        herdrTabId = null,
                        herdrWorkspaceId = null,
                        gentleMode = null,
                        redacted = false,
                        spawnedByHost = false,
                        theme = "gentle-cute",
                        todoCompleted = 2,
                        todoTotal = 5,
                        activeTask = "x",
                    ),
                ),
            )
            val back = db.agents().get("r1", "a1")!!
            assertEquals("gentle-cute", back.theme)
            assertEquals(2, back.todoCompleted)
            assertEquals(5, back.todoTotal)
            assertEquals("x", back.activeTask)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun v3FixtureOpensAndContainsSeededRows() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fixture = context.classLoader.getResourceAsStream("rp-v3-fixture.db")?.readBytes()
            ?: error("fixture rp-v3-fixture.db missing — generate with -Dparity.dbfixture.out")
        val target = context.getDatabasePath("fixture-open.db")
        target.parentFile?.mkdirs()
        target.outputStream().use { it.write(fixture) }
        val db = Room.databaseBuilder(context, RpDatabase::class.java, "fixture-open.db").allowMainThreadQueries().build()
        try {
            kotlinx.coroutines.runBlocking {
            val agentRow = db.agents().get("r1", "a-fixture")!!
            assertEquals("gentle-cute", agentRow.theme)
            assertEquals(2, agentRow.todoCompleted)
            assertEquals(5, agentRow.todoTotal)
            assertEquals("fixture task", agentRow.activeTask)
            // VALUE-level checks (verdict rev 3: existence is NOT enough):
            val promptRow = db.prompts().get("r1", "p-fixture")!!
            assertEquals("{\"id\":\"p-fixture\"}", promptRow.json)
            assertEquals("open", promptRow.state)
            assertEquals(2, promptRow.dangerRank)
            assertEquals(1700000000000L, promptRow.openedAt)
            assertNull(promptRow.resolvedBy)
            assertNull(promptRow.resolvedAt)
            val chatRows = db.chat().records("r1", "a-fixture")
            assertEquals(1, chatRows.size)
            assertEquals("{\"type\":\"message_end\"}", chatRows.first().json)
            assertEquals("event", chatRows.first().kind)
            assertEquals("e1", chatRows.first().epoch)
            assertEquals(1L, chatRows.first().seq)
            val outboxRows = db.outbox().all("r1")
            assertEquals(1, outboxRows.size)
            assertEquals("cm-fixture", outboxRows.first().clientMsgId)
            assertEquals("{\"text\":\"pending\"}", outboxRows.first().json)
            assertEquals(0, outboxRows.first().attempts)
            assertEquals(1700000000000L, outboxRows.first().createdAt)
            }
        } finally {
            db.close()
        }
    }
}
