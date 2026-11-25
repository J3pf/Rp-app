package dev.j3pf.rp.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.j3pf.rp.data.crypto.Aes256Gcm
import dev.j3pf.rp.data.crypto.AesGcmSecureCrypto
import dev.j3pf.rp.data.db.AgentEntity
import dev.j3pf.rp.data.db.ChatRecordEntity
import dev.j3pf.rp.data.db.DEDUP_PURGE_INTERVAL_MS
import dev.j3pf.rp.data.db.DEDUP_WINDOW_MS
import dev.j3pf.rp.data.db.DedupEntity
import dev.j3pf.rp.data.db.PromptEntity
import dev.j3pf.rp.data.db.PromptState
import dev.j3pf.rp.data.db.RecordKind
import dev.j3pf.rp.data.db.RpDatabase
import dev.j3pf.rp.data.secure.EncryptedKeyValueSecretStore
import dev.j3pf.rp.data.secure.InMemorySecretKeyValueBacking
import dev.j3pf.rp.data.secure.SecretKeyValueBacking
import dev.j3pf.rp.data.secure.Session
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PerfDataCoreTest {
    private lateinit var db: RpDatabase

    private val sampleSession =
        Session(
            relayId = "0123456789abcdef",
            relayUrl = "https://relay.example.com",
            relayName = "Studio Mac",
            deviceId = "dev-ios-1",
            deviceToken = "secret-device-token",
            pushKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        )

    @BeforeTest
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder<RpDatabase>()
                .setDriver(BundledSQLiteDriver())
                .build()
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun sessionV2UsesSingleWriteAndTwoColdStartReads() {
        val delegate = InMemorySecretKeyValueBacking()
        val readKeys = mutableListOf<String>()
        val writtenKeys = mutableListOf<String>()
        val countingBacking =
            object : SecretKeyValueBacking {
                override fun getString(key: String): String? {
                    readKeys += key
                    return delegate.getString(key)
                }

                override fun putString(
                    key: String,
                    value: String,
                ) {
                    writtenKeys += key
                    delegate.putString(key, value)
                }

                override fun remove(key: String) = delegate.remove(key)

                override fun clear() = delegate.clear()
            }

        val writer = EncryptedKeyValueSecretStore(backing = countingBacking)
        writtenKeys.clear()
        writer.save(sampleSession)

        // Writes master key + single packed session_v2 entry (not 6 separate session keys).
        assertEquals(
            listOf(EncryptedKeyValueSecretStore.K_MASTER_KEY, EncryptedKeyValueSecretStore.K_SESSION_V2),
            writtenKeys,
        )

        readKeys.clear()
        val coldStartReader = EncryptedKeyValueSecretStore(backing = countingBacking)
        assertEquals(sampleSession, coldStartReader.current())
        assertEquals(
            listOf(EncryptedKeyValueSecretStore.K_SESSION_V2, EncryptedKeyValueSecretStore.K_MASTER_KEY),
            readKeys,
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun legacySixKeyStoreLoadsTransparentlyAndUpgradesToV2OnSave() {
        val backing = InMemorySecretKeyValueBacking()
        val masterKey = Random(7).nextBytes(Aes256Gcm.KEY_BYTES)
        backing.putString(EncryptedKeyValueSecretStore.K_MASTER_KEY, Base64.encode(masterKey))
        val crypto = AesGcmSecureCrypto(masterKey, Random(11))

        fun putLegacy(
            key: String,
            value: String,
        ) = backing.putString(key, crypto.encrypt(key, value))

        putLegacy(EncryptedKeyValueSecretStore.K_RELAY_ID, sampleSession.relayId)
        putLegacy(EncryptedKeyValueSecretStore.K_URL, sampleSession.relayUrl)
        putLegacy(EncryptedKeyValueSecretStore.K_NAME, sampleSession.relayName)
        putLegacy(EncryptedKeyValueSecretStore.K_DEVICE_ID, sampleSession.deviceId)
        putLegacy(EncryptedKeyValueSecretStore.K_TOKEN, sampleSession.deviceToken)
        putLegacy(EncryptedKeyValueSecretStore.K_PUSH_KEY, sampleSession.pushKey)

        val store = EncryptedKeyValueSecretStore(backing = backing)
        assertEquals(sampleSession, store.current())
        assertNull(backing.getString(EncryptedKeyValueSecretStore.K_SESSION_V2))

        val updated = sampleSession.copy(relayName = "Upgraded Relay")
        store.save(updated)
        assertNotNull(backing.getString(EncryptedKeyValueSecretStore.K_SESSION_V2))
        assertNull(backing.getString(EncryptedKeyValueSecretStore.K_RELAY_ID))
        assertNull(backing.getString(EncryptedKeyValueSecretStore.K_URL))
        assertNull(backing.getString(EncryptedKeyValueSecretStore.K_NAME))
        assertNull(backing.getString(EncryptedKeyValueSecretStore.K_DEVICE_ID))
        assertNull(backing.getString(EncryptedKeyValueSecretStore.K_TOKEN))
        assertNull(backing.getString(EncryptedKeyValueSecretStore.K_PUSH_KEY))

        val reopened = EncryptedKeyValueSecretStore(backing = backing)
        assertEquals(updated, reopened.current())
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun legacySixKeyFieldSwapStillFailsAndPurges() {
        val backing = InMemorySecretKeyValueBacking()
        val masterKey = Random(13).nextBytes(Aes256Gcm.KEY_BYTES)
        backing.putString(EncryptedKeyValueSecretStore.K_MASTER_KEY, Base64.encode(masterKey))
        val crypto = AesGcmSecureCrypto(masterKey, Random(17))

        fun putLegacy(
            key: String,
            value: String,
        ) = backing.putString(key, crypto.encrypt(key, value))

        putLegacy(EncryptedKeyValueSecretStore.K_RELAY_ID, sampleSession.relayId)
        val rawUrl = crypto.encrypt(EncryptedKeyValueSecretStore.K_URL, sampleSession.relayUrl)
        backing.putString(EncryptedKeyValueSecretStore.K_URL, rawUrl)
        putLegacy(EncryptedKeyValueSecretStore.K_NAME, sampleSession.relayName)
        putLegacy(EncryptedKeyValueSecretStore.K_DEVICE_ID, sampleSession.deviceId)
        backing.putString(EncryptedKeyValueSecretStore.K_TOKEN, rawUrl) // swapped AAD
        putLegacy(EncryptedKeyValueSecretStore.K_PUSH_KEY, sampleSession.pushKey)

        val tampered = EncryptedKeyValueSecretStore(backing = backing)
        assertNull(tampered.current())
        assertNull(backing.getString(EncryptedKeyValueSecretStore.K_TOKEN))
    }

    @Test
    fun clearRemovesSessionV2EvenWhenBackingClearOnlyKnowsLegacyKeys() {
        val delegate = InMemorySecretKeyValueBacking()
        val legacyOnlyClearKeys =
            listOf(
                EncryptedKeyValueSecretStore.K_MASTER_KEY,
                EncryptedKeyValueSecretStore.K_RELAY_ID,
                EncryptedKeyValueSecretStore.K_URL,
                EncryptedKeyValueSecretStore.K_NAME,
                EncryptedKeyValueSecretStore.K_DEVICE_ID,
                EncryptedKeyValueSecretStore.K_TOKEN,
                EncryptedKeyValueSecretStore.K_PUSH_KEY,
            )
        val iosLikeBacking =
            object : SecretKeyValueBacking {
                override fun getString(key: String): String? = delegate.getString(key)

                override fun putString(
                    key: String,
                    value: String,
                ) = delegate.putString(key, value)

                override fun remove(key: String) = delegate.remove(key)

                override fun clear() {
                    legacyOnlyClearKeys.forEach { delegate.remove(it) }
                }
            }

        val store = EncryptedKeyValueSecretStore(backing = iosLikeBacking)
        store.save(sampleSession)
        assertNotNull(delegate.getString(EncryptedKeyValueSecretStore.K_SESSION_V2))

        store.clear()
        assertNull(store.current())
        assertNull(delegate.getString(EncryptedKeyValueSecretStore.K_SESSION_V2))
        assertNull(EncryptedKeyValueSecretStore(backing = iosLikeBacking).current())
    }

    @Test
    fun agentDaoGetByIdsAndPromptDaoBatchedUpsertOpen() =
        runTest {
            val agentDao = db.agents()
            val agents =
                listOf("a1", "a2", "a3").map { id ->
                    AgentEntity(
                        relayId = "r1",
                        agentId = id,
                        machineId = "m1",
                        machineName = "PC",
                        machineContext = "work",
                        machineColor = "#112233",
                        sessionName = null,
                        cwd = "/tmp",
                        gitBranch = null,
                        model = null,
                        status = "idle",
                        online = true,
                        lastSeenAt = 100L,
                        lastMessagePreview = null,
                        launcher = null,
                        herdrPaneId = null,
                        herdrTabId = null,
                        herdrWorkspaceId = null,
                        gentleMode = null,
                        redacted = false,
                        spawnedByHost = false,
                    )
                }
            agentDao.upsert(agents)
            val fetched = agentDao.getByIds("r1", listOf("a1", "a3", "missing"))
            assertEquals(setOf("a1", "a3"), fetched.map { it.agentId }.toSet())

            val promptDao = db.prompts()
            val now = 1_000_000L
            val p1 =
                PromptEntity(
                    relayId = "r1",
                    promptId = "p1",
                    agentId = "a1",
                    json = "{\"id\":\"p1\"}",
                    state = PromptState.OPEN,
                    dangerRank = 1,
                    openedAt = now,
                )
            val p2 = p1.copy(promptId = "p2", json = "{\"id\":\"p2\"}")
            promptDao.upsertOpen("r1", listOf(p1, p2), now)
            promptDao.markResolved("r1", "p1", by = "user", at = now + 100L)

            // Replaying both p1 and p2 inside the replay window must keep p1 resolved and still upsert p2.
            promptDao.upsertOpen("r1", listOf(p1, p2.copy(dangerRank = 2)), now + 200L)
            assertEquals(PromptState.RESOLVED, promptDao.get("r1", "p1")?.state)
            assertEquals(2, promptDao.get("r1", "p2")?.dangerRank)
            assertEquals(setOf("p1", "p2"), promptDao.getByIds("r1", listOf("p1", "p2")).map { it.promptId }.toSet())
        }

    @Test
    fun chatDaoRecentReturnsLatestBoundedRowsInAscendingOrder() =
        runTest {
            val chatDao = db.chat()
            chatDao.insert(
                (1L..15L).map { seq ->
                    ChatRecordEntity(
                        relayId = "r1",
                        agentId = "a1",
                        kind = RecordKind.EVENT,
                        epoch = "e1",
                        seq = seq,
                        json = "{\"seq\":$seq}",
                    )
                },
            )

            val recent = chatDao.recent("r1", "a1", limit = 5)
            assertEquals(listOf(11L, 12L, 13L, 14L, 15L), recent.map { it.seq })
        }

    @Test
    fun dedupDaoThrottlesPurgeToOncePerHour() =
        runTest {
            val dao = db.dedup()
            val now = 200_000_000L
            val agedAt = now - DEDUP_WINDOW_MS - 1_000L

            // Initial call triggers purge and deletes stale rows.
            dao.insert(DedupEntity("stale-1", agedAt))
            assertTrue(dao.firstSeen("fresh-1", now))
            assertNull(dao.seenAt("stale-1"))

            // Subsequent call within 1 hour skips purge DELETE, leaving a newly inserted aged row untouched.
            dao.insert(DedupEntity("stale-2", agedAt))
            assertTrue(dao.firstSeen("fresh-2", now + 60_000L))
            assertNotNull(dao.seenAt("stale-2"))

            // Once 1 hour elapses, purge runs again and removes stale-2.
            assertTrue(dao.firstSeen("fresh-3", now + DEDUP_PURGE_INTERVAL_MS))
            assertNull(dao.seenAt("stale-2"))
            assertFalse(dao.firstSeen("fresh-3", now + DEDUP_PURGE_INTERVAL_MS + 10L))
        }
}
