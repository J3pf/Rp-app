package dev.j3pf.rp.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        MachineEntity::class,
        AgentEntity::class,
        PromptEntity::class,
        ChatRecordEntity::class,
        OutboxEntity::class,
        DedupEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class RpDatabase : RoomDatabase() {
    abstract fun machines(): MachineDao
    abstract fun agents(): AgentDao
    abstract fun prompts(): PromptDao
    abstract fun chat(): ChatDao
    abstract fun outbox(): OutboxDao
    abstract fun dedup(): DedupDao
}
