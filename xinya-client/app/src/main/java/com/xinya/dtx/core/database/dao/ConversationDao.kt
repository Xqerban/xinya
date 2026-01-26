package com.xinya.dtx.core.database.dao

import androidx.room.*
import com.xinya.dtx.core.database.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    
    @Query("SELECT * FROM conversations WHERE patientId = :patientId ORDER BY createdAt DESC")
    fun observeByPatient(patientId: String): Flow<List<ConversationEntity>>
    
    @Query("SELECT * FROM conversations WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeBySession(sessionId: String): Flow<List<ConversationEntity>>
    
    @Query("SELECT * FROM conversations WHERE isSynced = 0 ORDER BY createdAt ASC")
    suspend fun getUnsynced(): List<ConversationEntity>
    
    @Insert
    suspend fun insert(conversation: ConversationEntity): Long
    
    @Query("UPDATE conversations SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)
    
    @Query("DELETE FROM conversations WHERE patientId = :patientId")
    suspend fun deleteByPatient(patientId: String)
}
