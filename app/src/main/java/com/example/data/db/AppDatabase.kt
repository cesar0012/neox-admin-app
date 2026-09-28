package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.DocumentItem
import com.example.data.model.JobProject
import com.example.data.model.MeetingNote
import com.example.data.model.MemoryChunk
import com.example.data.model.ProcessingQueueItem
import com.example.data.model.VaultEntry
import com.example.data.model.WorkTask

@Database(
    entities = [
        JobProject::class,
        WorkTask::class,
        MeetingNote::class,
        DocumentItem::class,
        MemoryChunk::class,
        VaultEntry::class,
        ProcessingQueueItem::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jobProjectDao(): JobProjectDao
    abstract fun taskDao(): TaskDao
    abstract fun meetingDao(): MeetingDao
    abstract fun documentDao(): DocumentDao
    abstract fun memoryDao(): MemoryDao
    abstract fun vaultDao(): VaultDao
    abstract fun queueDao(): QueueDao

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

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "omniwork_vault.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
