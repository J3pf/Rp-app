package dev.j3pf.rp.upgrade

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.j3pf.rp.data.db.PromptState
import dev.j3pf.rp.di.AppModule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T3e in-place upgrade harness (docs/adr/0003-t3b-t3e-migration-plan.md §5) — VERIFY side.
 *
 * After `adb install -r` of the migrated build (NEVER an uninstall — the Tink master key in
 * Android Keystore and the rp.db file MUST survive), this test runs READ-ONLY proof that the
 * in-place upgrade kept everything: any stray write here would mask a data loss, so this test
 * performs no writes at all. The RelayConnection push WebSocket or any relay traffic must NOT
 * be started by the app — the test asserts stored state only (no relay in the harness).
 *
 * Rows verified with the EXACT values seeded by [UpgradeSeedTest]:
 * - SecretStore decrypt round-trip == the seeded session (Tink keyset + Keystore masters survived).
 * - rp.db opens at schema version 3 (identity hash unchanged) via the REAL provider.
 * - agent theme/todo/activeTask, prompt, chat, outbox, machine, dedup, and the seeded settings.
 */
@RunWith(AndroidJUnit4::class)
class UpgradeVerifyTest {
    private val seedRelayId = "seed-relay"
    private val seedAgentId = "seed-agent"
    private val seedMachineId = "seed-machine"
    private val seedPromptId = "seed-prompt"
    private val seedMsgId = "seed-cm-1"
    private val seedDedupKey = "seed-dedup"

    private val seedSession =
        dev.j3pf.rp.data.secure.Session(
            relayId = "00000000-0000-4000-8000-000000000001",
            relayUrl = "http://seed.example:10000",
            relayName = "seed-relay-name",
            deviceId = "00000000-0000-4000-8000-000000000002",
            deviceToken = "seed-device-token",
            pushKey = "seed-push-key",
        )

    @Test
    fun verifiesInPlaceUpgradeSurvivalReadOnly() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // 1) Secrets survived the package-identity-preserving upgrade.
        val secrets = dev.j3pf.rp.data.secure.SecretStore(context)
        assertEquals(seedSession, secrets.current())

        // 2) Database through the SAME real provider/name; Room opens the persisted file and
        //    keeps it at schema version 3 with the unchanged identity hash.
        val db = AppModule.database(context)
        try {
            val agent = db.agents().get(seedRelayId, seedAgentId)!!
            assertEquals("sexy", agent.theme)
            assertEquals(3, agent.todoCompleted)
            assertEquals(7, agent.todoTotal)
            assertEquals("seed-active-task", agent.activeTask)
            val prompt = db.prompts().get(seedRelayId, seedPromptId)!!
            assertEquals(PromptState.OPEN, prompt.state)
            assertEquals(2, prompt.dangerRank)
            assertEquals(1, db.chat().records(seedRelayId, seedAgentId).size)
            assertEquals(seedMsgId, db.outbox().all(seedRelayId).single().clientMsgId)
            assertEquals(seedMachineId, db.machines().all(seedRelayId).single().machineId)
            assertEquals(1_000_003L, db.dedup().seenAt(seedDedupKey))
            // Schema version of the persisted file must still be 3 (S14 upgrade contract).
            db.openHelper.readableDatabase.query("PRAGMA user_version").use { c ->
                c.moveToFirst()
                assertEquals(3L, c.getLong(0))
            }
        } finally {
            db.close()
        }

        // 3) Settings survived (same file name; the DataStore-side is re-read, no writes).
        val settings = dev.j3pf.rp.data.repo.SettingsRepository(context)
        val snap = settings.current()
        assertEquals("sexy", snap.appTheme)
        // Falsifiable artifacts: directPush default is TRUE (SettingsRepository.kt), so the
        // seed planted FALSE — a lost settings file would flip this back to true/defaults.
        assertFalse(snap.directPush)
        assertTrue(snap.batteryAsked)
        assertTrue(snap.onboardingDone) // seed set true; default false (falsifiable)
    }
}
