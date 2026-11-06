package dev.j3pf.rp.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the trigger semantics of the production destructive fallback (AppModule.kt:48:
 * `fallbackToDestructiveMigration(dropAllTables = true)`, parity row DI-02, docs/parity/o1-data.md).
 * Any version the shipped migrations cannot reach (a fabricated v4, a relay-side downgrade) wipes
 * ALL user data instead of crashing. The v4 file is produced by taking a REAL v3 database created
 * by the current code and bumping its header version with raw SQL — no `4.json` exists.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DestructiveFallbackTest {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            RpDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    /** A valid v3 database created by the CURRENT code, then repainted as an unreachable v4. */
    private fun createUnreachableVersion4File(dbName: String) {
        // 1. Real v3 schema + one recognizable row through the raw helper.
        helper.createDatabase(dbName, 3).apply {
            execSQL(
                "INSERT INTO agents (relayId, agentId, machineId, machineName, machineContext, machineColor, status, online, redacted, spawnedByHost, todoCompleted, todoTotal) " +
                    "VALUES ('r1', 'a-lost', 'm1', 'PC', 'personal', '#FFF', 'idle', 1, 0, 0, 0, 0)",
            )
            close()
        }
        // 2. Bump user_version to 4 AND add a foreign legacy table; if migrations can't reach 4
        //    (no MIGRATION_3_4 exists), opening below exercises the exact AppModule.kt:48 path.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = context.getDatabasePath(dbName)
        assertTrue("v3 file must exist: $file", file.exists())
        val rawOpenHelper =
            FrameworkSQLiteOpenHelperFactory()
                .create(
                    SupportSQLiteOpenHelper.Configuration
                        .builder(context)
                        .name(dbName)
                        .callback(keepEverythingSupportSQLiteOpenHelperNoopCallback())
                        .build(),
                )
        rawOpenHelper.writableDatabase.use { db: SupportSQLiteDatabase ->
            db.execSQL("CREATE TABLE IF NOT EXISTS legacy_marker (id INTEGER NOT NULL PRIMARY KEY, note TEXT NOT NULL)")
            db.execSQL("INSERT INTO legacy_marker (id, note) VALUES (1, 'drop-me?')")
            db.execSQL("PRAGMA user_version = 4")
        }
        rawOpenHelper.close()
    }

    private fun keepEverythingSupportSQLiteOpenHelperNoopCallback() =
        object : SupportSQLiteOpenHelper.Callback(4) {
            override fun onCreate(db: SupportSQLiteDatabase) {}

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int,
            ) {}

            override fun onOpen(db: SupportSQLiteDatabase) {}
        }

    @Test
    fun newerDatabaseFallbackDropsLegacyTablesAndRecreatesCurrentSchema() = runBlocking {
        val dbName = "fallback-test"
        createUnreachableVersion4File(dbName)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val db =
            Room
                .databaseBuilder(context, RpDatabase::class.java, dbName)
                .addMigrations(RpDatabase.MIGRATION_1_2, RpDatabase.MIGRATION_2_3)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .allowMainThreadQueries()
                .build()
        try {
            // The rp tables exist again, recreated EMPTY at v3 (user data wiped):
            assertTrue(db.machines().all("r1").isEmpty())
            assertNull(db.agents().get("r1", "a-fixture"))
            assertNull(db.agents().get("r1", "a-lost"))
            assertTrue(db.agents().observe("r1").first().isEmpty())
            // dropAllTables also removed the foreign legacy table:
            val tables =
                db.openHelper.writableDatabase
                    .query("SELECT name FROM sqlite_master WHERE type='table'").use { c ->
                        buildSet { while (c.moveToNext()) add(c.getString(0)) }
                    }
            assertTrue(tables.containsAll(setOf("machines", "agents", "prompts", "chat_records", "outbox", "notification_dedup")))
            assertFalse("legacy_marker must be dropped by dropAllTables=true (observed real behavior)", tables.contains("legacy_marker"))
        } finally {
            db.close()
        }
    }
}
