package dev.j3pf.rp.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection

@ConstructedBy(RpDatabaseConstructor::class)
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
                override fun migrate(connection: SQLiteConnection) {
                    connection.prepare("ALTER TABLE machines ADD COLUMN quotasJson TEXT DEFAULT NULL").use { it.step() }
                }
            }

        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.prepare("ALTER TABLE agents ADD COLUMN theme TEXT DEFAULT NULL").use { it.step() }
                    connection.prepare("ALTER TABLE agents ADD COLUMN todoCompleted INTEGER NOT NULL DEFAULT 0").use { it.step() }
                    connection.prepare("ALTER TABLE agents ADD COLUMN todoTotal INTEGER NOT NULL DEFAULT 0").use { it.step() }
                    connection.prepare("ALTER TABLE agents ADD COLUMN activeTask TEXT DEFAULT NULL").use { it.step() }
                }
            }
    }
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object RpDatabaseConstructor : RoomDatabaseConstructor<RpDatabase> {
    // Room KMP 2.8.5 declares `abstract fun initialize(): T`; in Kotlin Multiplatform an
    // expect object must declare the abstract member so each actual supplies it.
    override fun initialize(): RpDatabase
}
