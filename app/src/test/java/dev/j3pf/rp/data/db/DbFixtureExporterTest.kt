package dev.j3pf.rp.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DbFixtureExporterTest {
    @Test
    fun exportsV3FixtureWhenOutputPropertySetOrChecksSeededRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db =
            Room.databaseBuilder(context, RpDatabase::class.java, "parity-fixture-gen.db")
                .allowMainThreadQueries()
                .build()

        try {
            db.agents().upsert(
                listOf(
                    AgentEntity(
                        relayId = "r1",
                        agentId = "a-fixture",
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
                        activeTask = "fixture task",
                    ),
                ),
            )
            db.prompts().upsert(
                listOf(
                    PromptEntity(
                        relayId = "r1",
                        promptId = "p-fixture",
                        agentId = "a-fixture",
                        json = "{\"id\":\"p-fixture\"}",
                        state = "open",
                        dangerRank = 2,
                        openedAt = 1700000000000,
                        resolvedBy = null,
                        resolvedAt = null,
                    ),
                ),
            )
            db.chat().insert(
                listOf(
                    ChatRecordEntity(
                        relayId = "r1",
                        agentId = "a-fixture",
                        kind = "event",
                        epoch = "e1",
                        seq = 1,
                        json = "{\"type\":\"message_end\"}",
                    ),
                ),
            )
            db.outbox().put(
                OutboxEntity(
                    clientMsgId = "cm-fixture",
                    relayId = "r1",
                    agentId = "a-fixture",
                    json = "{\"text\":\"pending\"}",
                    attempts = 0,
                    createdAt = 1700000000000L,
                ),
            )

            assertNotNull(db.agents().get("r1", "a-fixture"))
            assertNotNull(db.prompts().get("r1", "p-fixture"))
            assertEquals(1, db.chat().count("r1", "a-fixture"))
            assertEquals("cm-fixture", db.outbox().all("r1").single().clientMsgId)
        } finally {
            db.close()
        }

        // Trivial pass without the property (suite runs); export only when requested explicitly:
        // ./gradlew :app:testDirectDebugUnitTest --tests 'dev.j3pf.rp.data.db.DbFixtureExporterTest' -Dparity.dbfixture.out=app/src/test/resources/rp-v3-fixture.db
        val exported = System.getProperty("parity.dbfixture.out")?.also { outputPath ->
            val src = context.getDatabasePath("parity-fixture-gen.db")
            val destination = File(outputPath)
            destination.parentFile?.mkdirs()
            src.copyTo(destination, overwrite = true)
            val outputBytes = destination.length()
            assertTrue("exported fixture must be non-empty ($outputBytes bytes)", outputBytes > 0)
        }
    }
}
