package dev.j3pf.rp.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.testing.MigrationTestHelper
import dev.j3pf.rp.di.AppModule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifier fix (1), CRITICAL for S14: the destructive fallback must be pinned through THE REAL
 * production provider (`app/di/AppModule.kt` — `AppModule.database(ctx)` calling
 * `fallbackToDestructiveMigration(dropAllTables = true)`), NOT through a test-local builder.
 * If someone flips `dropAllTables` to false inside AppModule, the first test FAILS (Room throws on
 * the un-routable version or keeps the data), because the assertion pins the real configuration.
 * The second test proves the REAL provider still migrates a v2 file in place WITHOUT data loss.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppModuleDatabaseConfigTest {
    // Same name AppModule.database() uses — the file the next real install would open.
    private val prodDbName = "rp.db"

    private var db: RpDatabase? = null

    @After
    fun tearDown() {
        db?.close()
        db = null
    }

    @Test
    fun destructiveFallbackThroughTheRealProviderWipesLegacyTablesAndKeepsSchemaFresh() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Seed an UNROUTABLE version-4 file (no MIGRATION_3_4 exists anywhere).
        val helper =
            FrameworkSQLiteOpenHelperFactory()
                .create(
                    SupportSQLiteOpenHelper.Configuration
                        .builder(context)
                        .name(prodDbName)
                        .callback(LegacySchemaCallback())
                        .build(),
                )
        helper.writableDatabase.use { sq: SupportSQLiteDatabase ->
            sq.execSQL("CREATE TABLE IF NOT EXISTS legacy_marker (id INTEGER NOT NULL PRIMARY KEY, note TEXT NOT NULL)")
            sq.execSQL("INSERT INTO legacy_marker (id, note) VALUES (1, 'legacy-user-data')")
        }
        helper.close()

        // THE REAL production configuration (no test-local builder).
        val opened = AppModule.database(context)
        db = opened
        // Without dropAllTables=true Room aborts on the un-routable version: the fact that we
        // reached this line means the fallback fired. Now pin its OBSERVED data semantics:
        runBlocking {
            assertNull(opened.agents().get("r1", "any"))
            assertTrue(opened.machines().all("r1").isEmpty())
        }
        val tables =
            opened.openHelper.writableDatabase
                .query("SELECT name FROM sqlite_master WHERE type='table'").use { c ->
                    buildSet { while (c.moveToNext()) add(c.getString(0)) }
                }
        assertTrue(tables.containsAll(setOf("machines", "agents", "prompts", "chat_records", "outbox", "notification_dedup")))
        assertFalse("legacy_marker must be dropped by the REAL AppModule config", tables.contains("legacy_marker"))
    }

    @Test
    fun realProviderMigratesARealV2FileInPlaceKeepingData() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper =
            MigrationTestHelper(
                InstrumentationRegistry.getInstrumentation(),
                RpDatabase::class.java,
                emptyList(),
                FrameworkSQLiteOpenHelperFactory(),
            )
        helper.createDatabase(prodDbName, 2).apply {
            execSQL(
                "INSERT INTO agents (relayId, agentId, machineId, machineName, machineContext, machineColor, status, online, redacted, spawnedByHost) " +
                    "VALUES ('r1', 'a-inplace', 'm1', 'PC', 'personal', '#005577', 'idle', 1, 0, 0)",
            )
            close()
        }

        // The REAL provider (fallback + both migrations, AppModule.kt42-50) opens the SAME file.
        val opened = AppModule.database(context)
        db = opened
        val agent = runBlocking { opened.agents().get("r1", "a-inplace") }
        assertEquals("PC", agent?.machineName)
        // v3 defaults for pre-existing rows survive the real provider too.
        assertEquals(null, agent?.theme)
        assertEquals(0, agent?.todoCompleted)
        assertEquals(0, agent?.todoTotal)
        assertEquals(null, agent?.activeTask)

        // Write/keep theme+TODO values post-migration (v0.3.3 claim through the real provider).
        runBlocking {
            opened.agents().upsert(
                listOf(
                    requireNotNull(agent).copy(theme = "gentle-cute", todoCompleted = 2, todoTotal = 5, activeTask = "x"),
                ),
            )
        }
        runBlocking {
            val saved = opened.agents().get("r1", "a-inplace")!!
            assertEquals("gentle-cute", saved.theme)
            assertEquals(2, saved.todoCompleted)
            assertEquals(5, saved.todoTotal)
            assertEquals("x", saved.activeTask)
        }
    }

    /** Creates (once) a bare file whose header says version 4, with an unrelated legacy table. */
    private class LegacySchemaCallback : SupportSQLiteOpenHelper.Callback(4) {
        override fun onCreate(sq: SupportSQLiteDatabase) {
            // Deliberately NOT the rp schema: simulates a foreign/legacy upgraded file (or a relay
            // data restore) whose version no shipped migration can reach.
        }

        override fun onUpgrade(
            sq: SupportSQLiteDatabase,
            oldVersion: Int,
            newVersion: Int,
        ) {
        }
    }
}
