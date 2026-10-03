package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ConversationSession
import com.example.data.model.DocumentItem
import com.example.data.model.JobProject
import com.example.data.model.MeetingNote
import com.example.data.model.MeetingTaskItem
import com.example.data.model.MemoryChunk
import com.example.data.model.ProcessingQueueItem
import com.example.data.model.VaultEntry
import com.example.data.model.WorkTask

@Database(
    entities = [
        JobProject::class,
        WorkTask::class,
        MeetingNote::class,
        MeetingTaskItem::class,
        DocumentItem::class,
        MemoryChunk::class,
        VaultEntry::class,
        ProcessingQueueItem::class,
        ConversationSession::class,
        ChatMessageEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jobProjectDao(): JobProjectDao
    abstract fun taskDao(): TaskDao
    abstract fun meetingDao(): MeetingDao
    abstract fun meetingItemDao(): MeetingTaskItemDao
    abstract fun documentDao(): DocumentDao
    abstract fun memoryDao(): MemoryDao
    abstract fun vaultDao(): VaultDao
    abstract fun queueDao(): QueueDao
    abstract fun conversationDao(): ConversationDao
    abstract fun chatMsgDao(): ChatMsgDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop old tables to guarantee clean sync with version 2 schema
                db.execSQL("DROP TABLE IF EXISTS `job_projects`")
                db.execSQL("DROP TABLE IF EXISTS `work_tasks`")
                db.execSQL("DROP TABLE IF EXISTS `meeting_notes`")
                db.execSQL("DROP TABLE IF EXISTS `documents`")
                db.execSQL("DROP TABLE IF EXISTS `memory_chunks`")
                db.execSQL("DROP TABLE IF EXISTS `vault_entries`")
                db.execSQL("DROP TABLE IF EXISTS `processing_queue`")

                // Recreate all tables matching Room 2 schema
                db.execSQL("CREATE TABLE IF NOT EXISTS `job_projects` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `companyOrClient` TEXT NOT NULL, `colorHex` TEXT NOT NULL, `isPrimary` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `work_tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `jobTag` TEXT NOT NULL, `dueTimestamp` INTEGER NOT NULL, `reminderMinutesBefore` INTEGER NOT NULL, `isCompleted` INTEGER NOT NULL, `status` TEXT NOT NULL, `priority` TEXT NOT NULL, `originReference` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `meeting_notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `jobTag` TEXT NOT NULL, `dateTimestamp` INTEGER NOT NULL, `rawTranscript` TEXT NOT NULL, `executiveSummary` TEXT NOT NULL, `myActionItems` TEXT NOT NULL, `othersActionItems` TEXT NOT NULL, `keyDecisions` TEXT NOT NULL, `quotesJson` TEXT NOT NULL, `vaultEntryId` INTEGER NOT NULL, `isConcluded` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `documents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `jobTag` TEXT NOT NULL, `category` TEXT NOT NULL, `content` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `memory_chunks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sourceId` INTEGER NOT NULL, `sourceType` TEXT NOT NULL, `title` TEXT NOT NULL, `jobTag` TEXT NOT NULL, `chunkIndex` INTEGER NOT NULL, `content` TEXT NOT NULL, `keywords` TEXT NOT NULL, `exactQuote` TEXT NOT NULL, `dateString` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `vault_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `rawContent` TEXT NOT NULL, `sourceType` TEXT NOT NULL, `jobTag` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `retentionDays` INTEGER NOT NULL, `isProcessed` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `processing_queue` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `payload` TEXT NOT NULL, `operationType` TEXT NOT NULL, `status` TEXT NOT NULL, `retryCount` INTEGER NOT NULL, `lastError` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
            }
        }

        // v3: sesiones de conversación + persistencia del chat del asistente (aditiva, sin pérdida de datos)
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `conversation_sessions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `jobTag` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `lastActiveAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_messages` (" +
                        "`id` TEXT NOT NULL, `sessionId` INTEGER NOT NULL, " +
                        "`sender` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                        "`modelUsed` TEXT, `timestamp` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId` ON `chat_messages` (`sessionId`)")
            }
        }

        // v4: tipos de actividad (JUNTA/TAREA/...) y score de confianza en las tareas
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `work_tasks` ADD COLUMN `taskType` TEXT NOT NULL DEFAULT 'TAREA'")
                db.execSQL("ALTER TABLE `work_tasks` ADD COLUMN `confidence` TEXT NOT NULL DEFAULT 'ALTA'")
            }
        }

        // v5: tareas sin hora específica (el usuario/LLM no la mencionó)
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `work_tasks` ADD COLUMN `hasTime` INTEGER NOT NULL DEFAULT 1")
            }
        }

        // v6: ítems detectados en juntas, pendientes de revisión del usuario antes de
        // agregarse a sus tareas (pop-up de minutas)
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `meeting_task_items` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`meetingId` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`contextQuote` TEXT NOT NULL, " +
                        "`taskType` TEXT NOT NULL, " +
                        "`priority` TEXT NOT NULL, " +
                        "`dueTimestamp` INTEGER NOT NULL, " +
                        "`hasTime` INTEGER NOT NULL, " +
                        "`confidence` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_meeting_task_items_meetingId` ON `meeting_task_items` (`meetingId`)")
            }
        }

        // v7: eventos recurrentes (tipo + ancla) en tareas e items de junta
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `work_tasks` ADD COLUMN `recurrenceType` TEXT NOT NULL DEFAULT 'NINGUNA'")
                db.execSQL("ALTER TABLE `work_tasks` ADD COLUMN `recurrenceAnchor` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `meeting_task_items` ADD COLUMN `recurrenceType` TEXT NOT NULL DEFAULT 'NINGUNA'")
                db.execSQL("ALTER TABLE `meeting_task_items` ADD COLUMN `recurrenceAnchor` TEXT NOT NULL DEFAULT ''")
            }
        }

        // v8: atribucion de dueno (YO/OTRO/INDEFINIDO) en items de junta
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `meeting_task_items` ADD COLUMN `owner` TEXT NOT NULL DEFAULT 'INDEFINIDO'")
            }
        }

        // v9: anticipaciones de notificación por tarea/ítem (CSV; vacío = omisión global)
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `work_tasks` ADD COLUMN `notifLeadsCsv` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `meeting_task_items` ADD COLUMN `notifLeadsCsv` TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "omniwork_vault.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
