package dev.j3pf.rp.upgrade

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.j3pf.rp.data.db.AgentEntity
import dev.j3pf.rp.data.db.ChatRecordEntity
import dev.j3pf.rp.data.db.DedupEntity
import dev.j3pf.rp.data.db.MachineEntity
import dev.j3pf.rp.data.db.OutboxEntity
import dev.j3pf.rp.data.db.PromptEntity
import dev.j3pf.rp.data.db.PromptState
import dev.j3pf.rp.data.db.RecordKind
import dev.j3pf.rp.data.secure.Session
import dev.j3pf.rp.di.AppModule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T3e in-place upgrade harness (docs/adr/0003-t3b-t3e-migration-plan.md §5) — SEED side.
 *
 * Runs on a REAL device (no emulator on this machine, orchestrator-user decision; the report
 * stays `UNPROVEN - needs device` until the recorded output exists): install the PRE-move
 * directDebug build, run this test, then `adb install -r` the migrated build and run
 * [UpgradeVerifyTest]. NEVER a relay, NEVER an uninstall. Seed writes real production rows
 * through the REAL provider (AppModule.database) and the REAL Tink/Keystore SecretStore.
 */
@RunWith(AndroidJUnit4::class)
class UpgradeSeedTest {
    private val seedRelayId = "seed-relay"
    private val seedAgentId = "seed-agent"
    private val seedMachineId = "seed-machine"
    private val seedPromptId = "seed-prompt"
    private val seedMsgId = "seed-cm-1"
    private val seedDedupKey = "seed-dedup"

    private val seedSession =
        Session(
            relayId = "00000000-0000-4000-8000-000000000001",
            relayUrl = "http://seed.example:10000",
            relayName = "seed-relay-name",
            deviceId = "00000000-0000-4000-8000-000000000002",
            deviceToken = "seed-device-token",
            pushKey = "seed-push-key",
        )

    private fun db() = AppModule.database(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun seedsProductionDataForTheInPlaceUpgrade() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext

            val secrets =
                dev.j3pf.rp.data.secure
                    .SecretStore(context)
            secrets.save(seedSession)

            val db = db()
            try {
                db.machines().upsert(
                    listOf(
                        MachineEntity(
                            relayId = seedRelayId,
                            machineId = seedMachineId,
                            name = "seed-machine-name",
                            context = "seed-ctx",
                            color = "#112233",
                            icon = "seed-icon",
                            online = true,
                            agentCount = 1,
                            openPromptCount = 0,
                            hostOnline = true,
                            quotasJson = null,
                        ),
                    ),
                )
                db.agents().upsert(
                    listOf(
                        AgentEntity(
                            relayId = seedRelayId,
                            agentId = seedAgentId,
                            machineId = seedMachineId,
                            machineName = "seed-machine-name",
                            machineContext = "seed-ctx",
                            machineColor = "#112233",
                            sessionName = null,
                            cwd = "/seed",
                            gitBranch = null,
                            model = "kmp-seed-model",
                            status = "idle",
                            online = true,
                            lastSeenAt = 1_000_000L,
                            lastMessagePreview = "seed preview",
                            launcher = null,
                            herdrPaneId = null,
                            herdrTabId = null,
                            herdrWorkspaceId = null,
                            gentleMode = "calm",
                            redacted = false,
                            spawnedByHost = false,
                            theme = "sexy",
                            todoCompleted = 3,
                            todoTotal = 7,
                            activeTask = "seed-active-task",
                        ),
                    ),
                )
                db.prompts().upsert(
                    listOf(
                        PromptEntity(
                            relayId = seedRelayId,
                            promptId = seedPromptId,
                            agentId = seedAgentId,
                            json = """{"type":"seed"}""",
                            state = PromptState.OPEN,
                            dangerRank = 2,
                            openedAt = 1_000_001L,
                            resolvedBy = null,
                            resolvedAt = null,
                        ),
                    ),
                )
                db.chat().insert(
                    listOf(
                        ChatRecordEntity(
                            relayId = seedRelayId,
                            agentId = seedAgentId,
                            kind = RecordKind.ENTRY,
                            epoch = "seed-epoch",
                            seq = 1L,
                            json = """{"type":"seed"}""",
                        ),
                    ),
                )
                db.outbox().put(
                    OutboxEntity(
                        clientMsgId = seedMsgId,
                        relayId = seedRelayId,
                        agentId = seedAgentId,
                        json = """{"text":"seed"}""",
                        attempts = 0,
                        createdAt = 1_000_002L,
                    ),
                )
                db.dedup().insert(DedupEntity(key = seedDedupKey, seenAt = 1_000_003L))

                // Settings through the REAL DataStore-backed repository.
                val settings =
                    dev.j3pf.rp.data.repo
                        .SettingsRepository(context)
                settings.setAppTheme("sexy")
                settings.setDirectPush(false) // seeded FALSE so the verify assert is falsifiable (default is true)
                settings.setBatteryAsked(true)
                settings.setOnboardingDone(true)

                // Fail fast: EVERY seeded value must read back before this test returns.
                val agent = db.agents().get(seedRelayId, seedAgentId)!!
                assertEquals("sexy", agent.theme)
                assertEquals(3, agent.todoCompleted)
                assertEquals(7, agent.todoTotal)
                assertEquals("seed-active-task", agent.activeTask)
                val prompt = db.prompts().get(seedRelayId, seedPromptId)!!
                assertEquals(PromptState.OPEN, prompt.state)
                assertEquals(2, prompt.dangerRank)
                assertEquals(1, db.chat().records(seedRelayId, seedAgentId).size)
                assertEquals(
                    seedMsgId,
                    db
                        .outbox()
                        .all(seedRelayId)
                        .single()
                        .clientMsgId,
                )
                assertEquals(
                    seedMachineId,
                    db
                        .machines()
                        .all(seedRelayId)
                        .single()
                        .machineId,
                )
                assertEquals(1_000_003L, db.dedup().seenAt(seedDedupKey))
                val snap = settings.current()
                assertEquals("sexy", snap.appTheme)
                assertTrue(snap.directPush)
                assertTrue(snap.batteryAsked)
                // Secret read-back must decrypt to the SAME session (S18).
                assertEquals(seedSession, secrets.current())
            } finally {
                db.close()
            }
        }
}
