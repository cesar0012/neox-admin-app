package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ConversationSession
import com.example.data.model.DocumentItem
import com.example.data.model.JobProject
import com.example.data.model.MeetingNote
import com.example.data.model.MemoryChunk
import com.example.data.model.ProcessingQueueItem
import com.example.data.model.VaultEntry
import com.example.data.model.WorkTask
import kotlinx.coroutines.flow.Flow

@Dao
interface JobProjectDao {
    @Query("SELECT * FROM job_projects ORDER BY isPrimary DESC, name ASC")
    fun getAllJobs(): Flow<List<JobProject>>

    @Query("SELECT COUNT(*) FROM job_projects")
    suspend fun getJobCount(): Int

    @Query("SELECT * FROM job_projects ORDER BY isPrimary DESC, name ASC")
    suspend fun getAllJobsSync(): List<JobProject>

    @Query("DELETE FROM job_projects WHERE LOWER(name) = LOWER(:name)")
    suspend fun deleteJobByName(name: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: JobProject): Long

    @Delete
    suspend fun deleteJob(job: JobProject)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM work_tasks ORDER BY isCompleted ASC, dueTimestamp ASC")
    fun getAllTasks(): Flow<List<WorkTask>>

    @Query("SELECT * FROM work_tasks WHERE isCompleted = 0 ORDER BY dueTimestamp ASC")
    fun getPendingTasks(): Flow<List<WorkTask>>

    @Query("SELECT * FROM work_tasks WHERE dueTimestamp BETWEEN :start AND :end ORDER BY dueTimestamp ASC")
    fun getTasksForDateRange(start: Long, end: Long): Flow<List<WorkTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: WorkTask): Long

    @Update
    suspend fun updateTask(task: WorkTask)

    @Delete
    suspend fun deleteTask(task: WorkTask)

    @Query("UPDATE work_tasks SET isCompleted = :completed WHERE id = :id")
    suspend fun setTaskCompleted(id: Long, completed: Boolean)

    @Query("UPDATE work_tasks SET status = :status, isCompleted = :completed WHERE id = :id")
    suspend fun updateTaskStatus(id: Long, status: String, completed: Boolean)

    @Query("SELECT * FROM work_tasks WHERE jobTag = :jobTag ORDER BY dueTimestamp ASC")
    suspend fun getTasksByJobSync(jobTag: String): List<WorkTask>

    @Query("SELECT * FROM work_tasks ORDER BY dueTimestamp ASC")
    suspend fun getAllTasksSync(): List<WorkTask>

    @Query("UPDATE work_tasks SET dueTimestamp = :dueTimestamp WHERE id = :id")
    suspend fun updateTaskDue(id: Long, dueTimestamp: Long)

    @Query("DELETE FROM work_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)
}

@Dao
interface MeetingDao {
    @Query("SELECT * FROM meeting_notes ORDER BY dateTimestamp DESC")
    fun getAllMeetings(): Flow<List<MeetingNote>>

    @Query("SELECT * FROM meeting_notes WHERE id = :id")
    suspend fun getMeetingById(id: Long): MeetingNote?

    @Query("UPDATE meeting_notes SET isConcluded = :concluded WHERE id = :id")
    suspend fun setMeetingConcluded(id: Long, concluded: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeeting(meeting: MeetingNote): Long

    @Update
    suspend fun updateMeeting(meeting: MeetingNote)

    @Delete
    suspend fun deleteMeeting(meeting: MeetingNote)
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<DocumentItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: DocumentItem): Long

    @Update
    suspend fun updateDocument(doc: DocumentItem)

    @Delete
    suspend fun deleteDocument(doc: DocumentItem)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_chunks ORDER BY timestamp DESC")
    fun getAllChunks(): Flow<List<MemoryChunk>>

    @Query("SELECT * FROM memory_chunks")
    suspend fun getAllChunksList(): List<MemoryChunk>

    @Query("SELECT * FROM memory_chunks WHERE content LIKE '%' || :query || '%' OR keywords LIKE '%' || :query || '%' LIMIT 30")
    suspend fun searchChunks(query: String): List<MemoryChunk>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<MemoryChunk>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: MemoryChunk): Long

    @Query("DELETE FROM memory_chunks WHERE sourceId = :sourceId AND sourceType = :sourceType")
    suspend fun deleteChunksBySource(sourceId: Long, sourceType: String)
}

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_entries ORDER BY timestamp DESC")
    fun getAllVaultEntries(): Flow<List<VaultEntry>>

    @Query("SELECT * FROM vault_entries WHERE id = :id")
    suspend fun getVaultEntryById(id: Long): VaultEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultEntry(entry: VaultEntry): Long

    @Query("UPDATE vault_entries SET jobTag = :jobTag WHERE id = :id")
    suspend fun updateVaultEntryJobTag(id: Long, jobTag: String)

    @Query("DELETE FROM vault_entries WHERE timestamp < :cutoffTimestamp")
    suspend fun cleanExpiredEntries(cutoffTimestamp: Long)

    @Delete
    suspend fun deleteVaultEntry(entry: VaultEntry)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversation_sessions ORDER BY lastActiveAt DESC")
    fun getAllSessions(): Flow<List<ConversationSession>>

    @Query("SELECT * FROM conversation_sessions ORDER BY lastActiveAt DESC")
    suspend fun getAllSessionsSync(): List<ConversationSession>

    @Query("SELECT * FROM conversation_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): ConversationSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ConversationSession): Long

    @Query("UPDATE conversation_sessions SET lastActiveAt = :timestamp WHERE id = :id")
    suspend fun touchSession(id: Long, timestamp: Long)

    @Query("UPDATE conversation_sessions SET title = :title WHERE id = :id")
    suspend fun renameSession(id: Long, title: String)

    @Query("DELETE FROM conversation_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)
}

@Dao
interface ChatMsgDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesSync(sessionId: Long): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)
}

@Dao
interface QueueDao {
    @Query("SELECT * FROM processing_queue WHERE status = 'PENDING' ORDER BY createdAt ASC")
    fun getPendingQueue(): Flow<List<ProcessingQueueItem>>

    @Query("SELECT * FROM processing_queue WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingQueueList(): List<ProcessingQueueItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueItem(item: ProcessingQueueItem): Long

    @Update
    suspend fun updateItem(item: ProcessingQueueItem)

    @Query("UPDATE processing_queue SET status = :status, lastError = :error WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, error: String)

    @Delete
    suspend fun deleteItem(item: ProcessingQueueItem)
}
