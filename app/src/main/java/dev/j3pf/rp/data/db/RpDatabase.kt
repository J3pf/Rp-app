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
    version = 3,
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

        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE agents ADD COLUMN theme TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE agents ADD COLUMN todoCompleted INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE agents ADD COLUMN todoTotal INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE agents ADD COLUMN activeTask TEXT DEFAULT NULL")
                }
            }
    }
}
