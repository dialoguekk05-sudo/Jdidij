package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC LIMIT :limit")
    fun observeRecentMessages(limit: Int = 60): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLatestMessages(limit: Int = 12): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()
}

@Dao
interface MemoryFactDao {
    @Query("SELECT * FROM memory_facts ORDER BY createdAt DESC")
    fun observeAllFacts(): Flow<List<MemoryFactEntity>>

    @Query("SELECT * FROM memory_facts WHERE isEnabled = 1 ORDER BY createdAt DESC LIMIT 20")
    suspend fun getActiveFacts(): List<MemoryFactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFact(fact: MemoryFactEntity): Long

    @Update
    suspend fun updateFact(fact: MemoryFactEntity)

    @Query("DELETE FROM memory_facts WHERE id = :id")
    suspend fun deleteFactById(id: Long)

    @Query("DELETE FROM memory_facts")
    suspend fun clearAllFacts()
}

@Dao
interface ProjectBuilderDao {
    @Query("SELECT * FROM generated_projects ORDER BY updatedAt DESC")
    fun observeAllProjects(): Flow<List<GeneratedProjectEntity>>

    @Query("SELECT * FROM project_files WHERE projectId = :projectId ORDER BY relativePath ASC")
    fun observeFilesForProject(projectId: Long): Flow<List<ProjectFileEntity>>

    @Query("SELECT * FROM project_files WHERE projectId = :projectId ORDER BY relativePath ASC")
    suspend fun getFilesForProjectOnce(projectId: Long): List<ProjectFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: GeneratedProjectEntity): Long

    @Update
    suspend fun updateProject(project: GeneratedProjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<ProjectFileEntity>)

    @Update
    suspend fun updateFile(file: ProjectFileEntity)

    @Query("DELETE FROM project_files WHERE projectId = :projectId")
    suspend fun deleteFilesForProject(projectId: Long)

    @Query("DELETE FROM generated_projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: Long)
}
