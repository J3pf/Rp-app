package dev.j3pf.rp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MachineDao {
    @Query("SELECT * FROM machines WHERE relayId = :relayId ORDER BY name COLLATE NOCASE")
    fun observe(relayId: String): Flow<List<MachineEntity>>

    @Query("SELECT * FROM machines WHERE relayId = :relayId")
    suspend fun all(relayId: String): List<MachineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<MachineEntity>)

    @Query("DELETE FROM machines WHERE relayId = :relayId AND machineId IN (:ids)")
    suspend fun delete(
        relayId: String,
        ids: List<String>,
    )

    @Query("DELETE FROM machines WHERE relayId = :relayId AND machineId NOT IN (:keep)")
    suspend fun deleteNotIn(
        relayId: String,
        keep: List<String>,
    )

    @Transaction
    suspend fun syncWelcome(
        relayId: String,
        items: List<MachineEntity>,
        keep: List<String>,
    ) {
        upsert(items)
        deleteNotIn(relayId, keep)
    }

    @Query("DELETE FROM machines WHERE relayId = :relayId")
    suspend fun clear(relayId: String)
}

@Dao
interface AgentDao {
    @Query(
        """SELECT a.*, (SELECT COUNT(*) FROM prompts p WHERE p.relayId = a.relayId AND p.agentId = a.agentId AND p.state = 'open') AS openPrompts
           FROM agents a WHERE a.relayId = :relayId
           ORDER BY a.online DESC, a.lastSeenAt DESC""",
    )
    fun observe(relayId: String): Flow<List<AgentWithCounts>>

    @Query(
        """SELECT a.*, (SELECT COUNT(*) FROM prompts p WHERE p.relayId = a.relayId AND p.agentId = a.agentId AND p.state = 'open') AS openPrompts
           FROM agents a WHERE a.relayId = :relayId AND a.agentId = :agentId""",
    )
    fun observeOne(
        relayId: String,
        agentId: String,
    ): Flow<AgentWithCounts?>

    @Query("SELECT * FROM agents WHERE relayId = :relayId AND agentId = :agentId")
    suspend fun get(
        relayId: String,
        agentId: String,
    ): AgentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<AgentEntity>)

    @Query("DELETE FROM agents WHERE relayId = :relayId AND agentId IN (:ids)")
    suspend fun delete(
        relayId: String,
        ids: List<String>,
    )

    @Query("DELETE FROM agents WHERE relayId = :relayId AND agentId NOT IN (:keep)")
    suspend fun deleteNotIn(
        relayId: String,
        keep: List<String>,
    )

    @Transaction
    suspend fun syncWelcome(
        relayId: String,
        items: List<AgentEntity>,
        keep: List<String>,
    ) {
        upsert(items)
        deleteNotIn(relayId, keep)
    }

    @Query("UPDATE agents SET lastSeenAt = :now WHERE relayId = :relayId AND agentId = :agentId")
    suspend fun touch(
        relayId: String,
        agentId: String,
        now: Long,
    )

    @Query("DELETE FROM agents WHERE relayId = :relayId")
    suspend fun clear(relayId: String)
}

@Dao
interface PromptDao {
    @Query("SELECT * FROM prompts WHERE relayId = :relayId AND state = 'open' ORDER BY dangerRank DESC, openedAt ASC")
    fun observeOpen(relayId: String): Flow<List<PromptEntity>>

    @Query("SELECT * FROM prompts WHERE relayId = :relayId AND agentId = :agentId AND state = 'open' ORDER BY openedAt ASC")
    fun observeOpenFor(
        relayId: String,
        agentId: String,
    ): Flow<List<PromptEntity>>

    @Query("SELECT * FROM prompts WHERE relayId = :relayId AND promptId = :promptId")
    suspend fun get(
        relayId: String,
        promptId: String,
    ): PromptEntity?

    @Query("SELECT * FROM prompts WHERE relayId = :relayId AND promptId = :promptId")
    fun observeOne(
        relayId: String,
        promptId: String,
    ): Flow<PromptEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<PromptEntity>)

    @Query("UPDATE prompts SET state = 'resolved', resolvedBy = :by, resolvedAt = :at WHERE relayId = :relayId AND promptId = :promptId")
    suspend fun markResolved(
        relayId: String,
        promptId: String,
        by: String?,
        at: Long,
    )

    /** Tras `app.welcome`: lo que el relay ya no considera abierto se cierra localmente. */
    @Query(
        "UPDATE prompts SET state = 'resolved', resolvedBy = 'cancelled', resolvedAt = :at WHERE relayId = :relayId AND state = 'open' AND promptId NOT IN (:openIds)",
    )
    suspend fun resolveMissing(
        relayId: String,
        openIds: List<String>,
        at: Long,
    )

    @Transaction
    suspend fun syncWelcome(
        relayId: String,
        items: List<PromptEntity>,
        openIds: List<String>,
        now: Long,
    ) {
        upsert(items)
        resolveMissing(relayId, openIds, now)
    }

    @Query("DELETE FROM prompts WHERE state = 'resolved' AND resolvedAt < :before")
    suspend fun purgeResolved(before: Long)

    @Query("DELETE FROM prompts WHERE relayId = :relayId")
    suspend fun clear(relayId: String)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_records WHERE relayId = :relayId AND agentId = :agentId ORDER BY kind ASC, seq ASC, id ASC")
    suspend fun records(
        relayId: String,
        agentId: String,
    ): List<ChatRecordEntity>

    @Insert
    suspend fun insert(items: List<ChatRecordEntity>)

    @Query("DELETE FROM chat_records WHERE relayId = :relayId AND agentId = :agentId AND kind = :kind")
    suspend fun deleteKind(
        relayId: String,
        agentId: String,
        kind: String,
    )

    @Query(
        "DELETE FROM chat_records WHERE relayId = :relayId AND agentId = :agentId AND kind = 'event' AND (epoch IS NOT :epoch OR seq < :beforeSeq)",
    )
    suspend fun deleteEventsBefore(
        relayId: String,
        agentId: String,
        epoch: String?,
        beforeSeq: Long,
    )

    @Query("DELETE FROM chat_records WHERE relayId = :relayId AND agentId = :agentId")
    suspend fun clearAgent(
        relayId: String,
        agentId: String,
    )

    @Query("SELECT COUNT(*) FROM chat_records WHERE relayId = :relayId AND agentId = :agentId")
    suspend fun count(
        relayId: String,
        agentId: String,
    ): Int

    /** Conserva sólo los `keep` registros más recientes de un agente. */
    @Query(
        """DELETE FROM chat_records WHERE relayId = :relayId AND agentId = :agentId AND id NOT IN
           (SELECT id FROM chat_records WHERE relayId = :relayId AND agentId = :agentId ORDER BY id DESC LIMIT :keep)""",
    )
    suspend fun trim(
        relayId: String,
        agentId: String,
        keep: Int,
    )

    @Query("DELETE FROM chat_records WHERE relayId = :relayId")
    suspend fun clear(relayId: String)
}

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(item: OutboxEntity)

    @Query("SELECT * FROM outbox WHERE clientMsgId = :id")
    suspend fun get(id: String): OutboxEntity?

    @Query("SELECT * FROM outbox WHERE relayId = :relayId ORDER BY createdAt")
    suspend fun all(relayId: String): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE clientMsgId = :id")
    suspend fun delete(id: String)

    @Query("UPDATE outbox SET attempts = attempts + 1 WHERE clientMsgId = :id")
    suspend fun bump(id: String)

    @Query("DELETE FROM outbox")
    suspend fun clear()
}

@Dao
abstract class DedupDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(item: DedupEntity): Long

    @Query("DELETE FROM notification_dedup WHERE seenAt < :before")
    abstract suspend fun purge(before: Long)

    @Query("DELETE FROM notification_dedup WHERE `key` = :key")
    abstract suspend fun remove(key: String)

    @Query("DELETE FROM notification_dedup")
    abstract suspend fun clear()

    @Transaction
    open suspend fun firstSeen(
        key: String,
        now: Long,
    ): Boolean {
        purge(now - 24 * 3600 * 1000L)
        return insert(DedupEntity(key, now)) != -1L
    }
}
