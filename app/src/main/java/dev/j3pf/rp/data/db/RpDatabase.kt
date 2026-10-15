package dev.j3pf.rp.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MachineEntity::class,
        AgentEntity::class,
        PromptEntity::class,
        ChatRecordEntity::class,
        OutboxEntity::class,
        DedupEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class RpDatabase : RoomDatabase() {
    abstract fun machines(): MachineDao

    abstract fun agents(): AgentDao

    abstract fun prompts(): PromptDao

    abstract fun chat(): ChatDao

    abstract fun outbox(): OutboxDao

    abstract fun dedup(): DedupDao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE machines ADD COLUMN quotasJson TEXT DEFAULT NULL")
                }
            }
    }
}
