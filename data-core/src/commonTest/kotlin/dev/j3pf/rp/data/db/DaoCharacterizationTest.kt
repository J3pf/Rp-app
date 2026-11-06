package dev.j3pf.rp.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest as After
import kotlin.test.BeforeTest as Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Characterization (T1/Gate A) of the Room DAO contracts on UNCHANGED production code, in-memory
 * Room with the bundled SQLite driver. Pins DB-04..DB-12 of docs/parity/o1-data.md — behaviors the
 * future Room KMP port (T3, S14/S15) must preserve exactly, including the prompt replay window.
 */
class DaoCharacterizationTest {
    private lateinit var db: RpDatabase

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder<RpDatabase>()
                .setDriver(BundledSQLiteDriver())
                .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun machine(
        id: String,
        name: String,
    ) = MachineEntity(
        relayId = "r1",
        machineId = id,
        name = name,
        context = "ctx",
        color = "#112233",
        icon = null,
        online = true,
        agentCount = 0,
        openPromptCount = 0,
        hostOnline = true,
        quotasJson = null,
    )

    private fun agent(
        id: String,
        name: String = "agent-$id",
        online: Boolean = true,
        lastSeenAt: Long? = null,
    ) = AgentEntity(
        relayId = "r1",
        agentId = id,
        machineId = "m1",
        machineName = "machine",
        machineContext = "ctx",
        machineColor = "#112233",
        sessionName = null,
        cwd = "/tmp",
        gitBranch = null,
        model = null,
        status = "idle",
        online = online,
        lastSeenAt = lastSeenAt,
        lastMessagePreview = null,
        launcher = null,
        herdrPaneId = null,
        herdrTabId = null,
        herdrWorkspaceId = null,
        gentleMode = null,
        redacted = false,
        spawnedByHost = false,
        theme = null,
        todoCompleted = 0,
        todoTotal = 0,
        activeTask = null,
    )

    private fun prompt(
        id: String,
        state: String = PromptState.OPEN,
        dangerRank: Int = 1,
        openedAt: Long = 0L,
        resolvedBy: String? = null,
        resolvedAt: Long? = null,
    ) = PromptEntity(
        relayId = "r1",
        promptId = id,
        agentId = "a1",
        json = "{\"id\":\"$id\"}",
        state = state,
        dangerRank = dangerRank,
        openedAt = openedAt,
        resolvedBy = resolvedBy,
        resolvedAt = resolvedAt,
    )

    private fun chatRecord(
        kind: String,
        epoch: String? = "e1",
        seq: Long,
    ) = ChatRecordEntity(
        relayId = "r1",
        agentId = "a1",
        kind = kind,
        epoch = epoch,
        seq = seq,
        json = "{\"seq\":$seq}",
    )

    // ---- MachineDao / AgentDao.syncWelcome (DB-05) ----

    @Test
    fun machineSyncWelcomeUpsertsAndPrunesRowsNotInKeep() = runTest {
        val dao = db.machines()
        dao.upsert(listOf(machine("m1", "M1"), machine("m2", "M2")))
        dao.syncWelcome("r1", listOf(machine("m2", "M2-renamed")), keep = listOf("m2"))
        assertNull(dao.all("r1").firstOrNull { it.machineId == "m1" })
        assertEquals("M2-renamed", dao.all("r1").first { it.machineId == "m2" }.name)
        assertEquals(setOf("m2"), dao.all("r1").map { it.machineId }.toSet())
    }

    @Test
    fun agentSyncWelcomeUpsertsAndPrunesRowsNotInKeep() = runTest {
        val dao = db.agents()
        dao.upsert(listOf(agent("a1"), agent("a2")))
        dao.syncWelcome("r1", listOf(agent("a2", online = false)), keep = listOf("a2"))
        assertNull(dao.get("r1", "a1"))
        assertEquals(setOf("a2"), dao.observe("r1").first().map { it.agent.agentId }.toSet())
        // REPLACEd rows are updated in place: new field values win, no duplicate row.
        dao.syncWelcome("r1", listOf(agent("a2", online = true, lastSeenAt = 5L)), keep = listOf("a2"))
        val after = dao.get("r1", "a2")!!
        assertTrue(after.online)
        assertEquals(5L, after.lastSeenAt)
        assertEquals(1, dao.observe("r1").first().size)
    }

    @Test
    fun touchBumpsOnlyLastSeenAtOfTheTargetAgent() = runTest {
        val dao = db.agents()
        dao.upsert(listOf(agent("a1", lastSeenAt = 1L), agent("a2", lastSeenAt = 1L)))
        dao.touch("r1", "a1", now = 99L)
        assertEquals(99L, dao.get("r1", "a1")?.lastSeenAt)
        assertEquals(1L, dao.get("r1", "a2")?.lastSeenAt)
    }

    @Test
    fun agentObserveCountsOpenPromptsAndExcludesResolvedOnes() = runTest {
        db.agents().upsert(listOf(agent("a1")))
        val now = 1_000L
        db.prompts().upsert(
            listOf(
                prompt("p1", state = PromptState.OPEN, openedAt = now),
                prompt("p2", state = PromptState.OPEN, openedAt = now + 1),
                prompt("p3", state = PromptState.RESOLVED, openedAt = now, resolvedAt = now),
            ),
        )
        val counts = db.agents().observe("r1").first()
        assertEquals(2, counts.first { it.agent.agentId == "a1" }.openPrompts)
    }

    // ---- PromptDao replay window (DB-06/DB-07/DB-08) ----

    @Test
    fun upsertOpenWithinReplayWindowDoesNotReviveResolvedPrompt() = runTest {
        val dao = db.prompts()
        val now = 100_000L
        // Local state: prompt opened then resolved 1s before `now` (inside the 10-min window).
        dao.upsertOpen("r1", listOf(prompt("p1", openedAt = now)), now)
        dao.markResolved("r1", "p1", by = "user", at = now - 1_000L)
        assertEquals(PromptState.RESOLVED, dao.get("r1", "p1")?.state)
        assertEquals("user", dao.get("r1", "p1")?.resolvedBy)

        // Reply-replay while inside the window: not revived, resolution preserved.
        dao.upsertOpen("r1", listOf(prompt("p1", openedAt = now)), now)
        assertEquals(PromptState.RESOLVED, dao.get("r1", "p1")?.state)
        assertEquals("user", dao.get("r1", "p1")?.resolvedBy)

        // After the window: a legitimately reused id opens again.
        dao.upsertOpen("r1", listOf(prompt("p1", openedAt = now + PromptState.REPLAY_WINDOW_MS + 1)), now + PromptState.REPLAY_WINDOW_MS + 2)
        assertEquals(PromptState.OPEN, dao.get("r1", "p1")?.state)
    }

    @Test
    fun observeOpenOrdersByDangerRankDescThenOpenedAtAsc() = runTest {
        val dao = db.prompts()
        val now = 10_000L
        dao.upsert(
            listOf(
                prompt("low-old", dangerRank = 0, openedAt = now),
                prompt("high-old", dangerRank = 2, openedAt = now + 1),
                prompt("high-new", dangerRank = 2, openedAt = now + 2),
                prompt("mid-old", dangerRank = 1, openedAt = now + 3),
            ),
        )
        val ids = dao.observeOpen("r1").first().map { it.promptId }
        assertEquals(listOf("high-old", "high-new", "mid-old", "low-old"), ids)
    }

    @Test
    fun resolveMissingCancelsOpenPromptsMissingFromWelcome() = runTest {
        val dao = db.prompts()
        val now = 10_000L
        dao.upsert(listOf(prompt("p-kept", openedAt = now), prompt("p-vanished", openedAt = now + 1)))
        dao.syncWelcome("r1", items = listOf(prompt("p-kept", openedAt = now)), openIds = listOf("p-kept"), now = now + 5)
        assertEquals(PromptState.OPEN, dao.get("r1", "p-kept")?.state)
        val vanished = dao.get("r1", "p-vanished")!!
        assertEquals(PromptState.RESOLVED, vanished.state)
        assertEquals("cancelled", vanished.resolvedBy)
        assertEquals(now + 5, vanished.resolvedAt)
    }

    @Test
    fun purgeResolvedRemovesOnlyResolvedRowsOlderThanCutoff() = runTest {
        val dao = db.prompts()
        val now = 10_000L
        dao.upsert(
            listOf(
                prompt("old-resolved", state = PromptState.RESOLVED, openedAt = now, resolvedAt = now),
                prompt("fresh-resolved", state = PromptState.RESOLVED, openedAt = now, resolvedAt = now + 10),
                prompt("still-open", openedAt = now),
            ),
        )
        dao.purgeResolved(now + 6)
        assertNull(dao.get("r1", "old-resolved")) // resolvedAt=now < cutoff
        assertEquals("fresh-resolved", dao.get("r1", "fresh-resolved")?.promptId)
        assertEquals("still-open", dao.get("r1", "still-open")?.promptId)
    }

    // ---- ChatDao (DB-09/DB-10) ----

    @Test
    fun deleteEventsBeforeKeepsEntriesAndNewerEpochEventsOnly() = runTest {
        val dao = db.chat()
        dao.insert(
            listOf(
                chatRecord(kind = RecordKind.ENTRY, seq = 1),
                chatRecord(kind = RecordKind.EVENT, epoch = "e0", seq = 7), // different epoch -> removed
                chatRecord(kind = RecordKind.EVENT, epoch = "e1", seq = 3), // seq < 5 -> removed
                chatRecord(kind = RecordKind.EVENT, epoch = "e1", seq = 9), // kept
            ),
        )
        dao.deleteEventsBefore("r1", "a1", epoch = "e1", beforeSeq = 5)
        val remaining = dao.records("r1", "a1")
        assertEquals(2, remaining.size)
        val entry = remaining.first { it.kind == RecordKind.ENTRY }
        assertEquals(1L, entry.seq)
        val event = remaining.first { it.kind == RecordKind.EVENT }
        assertEquals("e1", event.epoch)
        assertEquals(9L, event.seq)
    }

    @Test
    fun trimKeepsOnlyTheNewestRowsByAutoincrementId() = runTest {
        val dao = db.chat()
        dao.insert((1..6).map { chatRecord(kind = RecordKind.EVENT, seq = it.toLong()) })
        dao.trim("r1", "a1", keep = 3)
        assertEquals(listOf(4L, 5L, 6L), dao.records("r1", "a1").map { it.seq })
    }

    // ---- OutboxDao (DB-11: idempotency, attempts, ordering) ----

    @Test
    fun outboxPutReplacesOnClientMsgIdAndBumpIncrementsAttempts() = runTest {
        val dao = db.outbox()
        dao.put(OutboxEntity(clientMsgId = "m1", relayId = "r1", agentId = "a1", json = "{}", createdAt = 10))
        dao.put(OutboxEntity(clientMsgId = "m1", relayId = "r1", agentId = "a1", json = "{}", createdAt = 20))
        dao.put(OutboxEntity(clientMsgId = "m0", relayId = "r1", agentId = "a1", json = "{}", createdAt = 1))
        // all() is ordered by createdAt regardless of insert order; REPLACE keeps a single m1 row.
        val all = dao.all("r1")
        assertEquals(listOf("m0", "m1"), all.map { it.clientMsgId })
        assertEquals(20L, all.last().createdAt)
        dao.bump("m1")
        dao.bump("m1")
        assertEquals(2, dao.all("r1").first { it.clientMsgId == "m1" }.attempts)
        dao.delete("m1")
        assertNull(dao.get("m1"))
    }

    @Test
    fun outboxScopedByRelayId() = runTest {
        val dao = db.outbox()
        dao.put(OutboxEntity(clientMsgId = "m1", relayId = "r1", agentId = "a1", json = "{}", createdAt = 1))
        dao.put(OutboxEntity(clientMsgId = "m2", relayId = "r2", agentId = "a1", json = "{}", createdAt = 2))
        assertEquals(listOf("m1"), dao.all("r1").map { it.clientMsgId })
    }

    // ---- DedupDao (DB-12: 24h window, mark refresh, firstSeen) ----

    @Test
    fun dedupFirstSeenIsTrueOncePerKeyAndPurgeRemovesAgedRows() = runTest {
        val dao = db.dedup()
        val now = 1_000_000L
        assertTrue(dao.firstSeen("prompt-1", now))
        assertFalse(dao.firstSeen("prompt-1", now + 1)) // duplicate within the same day
        assertTrue(dao.firstSeen("prompt-2", now + 2))
        // mark refreshes the timestamp so seenSince stays true for the refreshed key
        dao.mark("prompt-2", now + 100)
        assertTrue(dao.seenSince("prompt-2", windowMs = 200, now = now + 150))
        // purge removes only rows older than the cutoff
        dao.insert(DedupEntity("prompt-3", now - 30_000L))
        dao.purge(now - 10_000L)
        assertFalse(dao.seenSince("prompt-3", windowMs = 100_000L, now = now))
        assertTrue(dao.seenSince("prompt-1", windowMs = 100_000L, now = now))
    }

    // ---- PromptDao.recentlyResolved (pre-move characterization: the production default-clock
    // semantic is about to become an ALWAYS-EXPLICIT caller argument for KMP compilability) ----

    @Test
    fun recentlyResolvedIsTrueOnlyInsideTheReplayWindowForResolvedRows() = runTest {
        val dao = db.prompts()
        val now = 9_000_000L
        // Resolution inside the window -> replay suppressed.
        db.prompts().upsert(listOf(prompt("p-in-window", state = PromptState.RESOLVED, resolvedAt = now - 100)))
        assertTrue(dao.recentlyResolved("r1", "p-in-window", now = now))
        // Resolution OLDer than the replay window -> NOT suppressed.
        db.prompts().upsert(listOf(prompt("p-out-of-window", state = PromptState.RESOLVED, resolvedAt = now - PromptState.REPLAY_WINDOW_MS - 1)))
        assertFalse(dao.recentlyResolved("r1", "p-out-of-window", now = now))
        // Boundary is INCLUSIVE on resolvedAt >= now - window.
        db.prompts().upsert(listOf(prompt("p-edge", state = PromptState.RESOLVED, resolvedAt = now - PromptState.REPLAY_WINDOW_MS)))
        assertTrue(dao.recentlyResolved("r1", "p-edge", now = now))
        // An OPEN prompt is never "recently resolved" regardless of data.
        db.prompts().upsert(listOf(prompt("p-open", state = PromptState.OPEN, resolvedAt = now)))
        assertFalse(dao.recentlyResolved("r1", "p-open", now = now))
        // Unknown prompt id -> false, no crash.
        assertFalse(dao.recentlyResolved("r1", "p-missing", now = now))
    }

    @Test
    fun dedupSeenSinceExpiresExactlyAtWindowEdge() = runTest {
        val dao = db.dedup()
        val now = 5_000_000L
        dao.mark("k", now)
        // Boundary is INCLUSIVE: `at >= now - windowMs`, so the key is still valid exactly one full window after marking.
        assertTrue(dao.seenSince("k", windowMs = 1_000, now = now + 999))
        assertTrue(dao.seenSince("k", windowMs = 1_000, now = now + 1_000))
        assertFalse(dao.seenSince("k", windowMs = 1_000, now = now + 1_001))
    }
}
