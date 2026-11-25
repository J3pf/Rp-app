package dev.j3pf.rp.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseMigrationTest {
    private val testDb = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            RpDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrationFrom2To3PreservesDataAndAddsNewColumnsWithDefaults() {
        // 1. Crear base de datos en versión 2 e insertar datos de agente
        helper.createDatabase(testDb, 2).apply {
            execSQL(
                """
                INSERT INTO agents (
                    relayId, agentId, machineId, machineName, machineContext, machineColor,
                    sessionName, cwd, gitBranch, model, status, online, lastSeenAt,
                    lastMessagePreview, launcher, herdrPaneId, herdrTabId, herdrWorkspaceId,
                    gentleMode, redacted, spawnedByHost
                ) VALUES (
                    'relay-1', 'agent-alpha', 'm-1', 'Laptop', 'work', '#7AA2F7',
                    'Dev Session', '/workspace', 'feature/test', 'sonnet', 'working', 1, 1700000000000,
                    'Hello world', 'pi', 'pane-1', 'tab-1', 'ws-1',
                    'auto', 0, 1
                )
                """.trimIndent(),
            )
            close()
        }

        // 2. Ejecutar migración a versión 3 y validar esquema
        val dbV3 = helper.runMigrationsAndValidate(testDb, 3, true, RpDatabase.MIGRATION_2_3)

        // 3. Verificar que los datos previos no se perdieron y los nuevos campos tienen sus valores por defecto
        val cursor =
            dbV3.query(
                "SELECT agentId, machineName, theme, todoCompleted, todoTotal, activeTask FROM agents WHERE agentId = 'agent-alpha'",
            )
        try {
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("agent-alpha", cursor.getString(0))
            assertEquals("Laptop", cursor.getString(1))
            assertNull(cursor.getString(2)) // theme: null
            assertEquals(0, cursor.getInt(3)) // todoCompleted: 0
            assertEquals(0, cursor.getInt(4)) // todoTotal: 0
            assertNull(cursor.getString(5)) // activeTask: null
        } finally {
            cursor.close()
            dbV3.close()
        }
    }

    @Test
    fun migrationFrom1To2To3EndToEnd() {
        helper.createDatabase(testDb, 1).apply {
            execSQL(
                "INSERT INTO machines (relayId, machineId, name, context, color, online, hostOnline, agentCount, openPromptCount) " +
                    "VALUES ('r1', 'm1', 'PC', 'personal', '#FFF', 1, 1, 1, 0)",
            )
            execSQL(
                """
                INSERT INTO agents (
                    relayId, agentId, machineId, machineName, machineContext, machineColor,
                    sessionName, cwd, gitBranch, model, status, online, lastSeenAt,
                    lastMessagePreview, launcher, herdrPaneId, herdrTabId, herdrWorkspaceId,
                    gentleMode, redacted, spawnedByHost
                ) VALUES (
                    'r1', 'a1', 'm1', 'PC', 'personal', '#FFF',
                    'S1', '/dir', 'main', 'm', 'idle', 1, 100,
                    'msg', 'l', 'p', 't', 'w',
                    'norm', 0, 0
                )
                """.trimIndent(),
            )
            close()
        }

        val dbFinal = helper.runMigrationsAndValidate(testDb, 3, true, RpDatabase.MIGRATION_1_2, RpDatabase.MIGRATION_2_3)

        val cursor = dbFinal.query("SELECT todoCompleted, todoTotal FROM agents WHERE agentId = 'a1'")
        try {
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
            assertEquals(0, cursor.getInt(1))
        } finally {
            cursor.close()
            dbFinal.close()
        }
    }

    @Test
    fun migrationFrom3To4PreservesChatRecordsAndCreatesIdIndex() {
        helper.createDatabase(testDb, 3).apply {
            execSQL(
                "INSERT INTO chat_records (relayId, agentId, kind, epoch, seq, json) " +
                    "VALUES ('r1', 'a1', 'event', 'e1', 42, '{\"type\":\"message_end\"}')",
            )
            close()
        }

        val dbV4 = helper.runMigrationsAndValidate(testDb, 4, true, RpDatabase.MIGRATION_3_4)
        try {
            dbV4.query("SELECT relayId, agentId, kind, epoch, seq, json FROM chat_records WHERE agentId = 'a1'").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("r1", cursor.getString(0))
                assertEquals("a1", cursor.getString(1))
                assertEquals("event", cursor.getString(2))
                assertEquals("e1", cursor.getString(3))
                assertEquals(42L, cursor.getLong(4))
                assertEquals("{\"type\":\"message_end\"}", cursor.getString(5))
            }
            dbV4
                .query(
                    "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_chat_records_relayId_agentId_id'",
                ).use { cursor ->
                    assertEquals(1, cursor.count)
                }
        } finally {
            dbV4.close()
        }
    }

    @Test
    fun migrationFrom1To4EndToEnd() {
        helper.createDatabase(testDb, 1).apply {
            execSQL(
                "INSERT INTO machines (relayId, machineId, name, context, color, online, hostOnline, agentCount, openPromptCount) " +
                    "VALUES ('r1', 'm1', 'PC', 'personal', '#FFF', 1, 1, 1, 0)",
            )
            execSQL(
                "INSERT INTO chat_records (relayId, agentId, kind, epoch, seq, json) " +
                    "VALUES ('r1', 'a1', 'event', 'e1', 7, '{\"type\":\"agent_settled\"}')",
            )
            close()
        }

        val dbFinal =
            helper.runMigrationsAndValidate(
                testDb,
                4,
                true,
                RpDatabase.MIGRATION_1_2,
                RpDatabase.MIGRATION_2_3,
                RpDatabase.MIGRATION_3_4,
            )
        try {
            dbFinal.query("SELECT seq FROM chat_records WHERE agentId = 'a1'").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals(7L, cursor.getLong(0))
            }
        } finally {
            dbFinal.close()
        }
    }
}
