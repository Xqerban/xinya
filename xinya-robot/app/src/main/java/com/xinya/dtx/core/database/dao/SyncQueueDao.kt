package com.xinya.dtx.core.database.dao

import androidx.room.*
import com.xinya.dtx.core.database.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {
    
    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPending(): List<SyncQueueEntity>
    
    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>
    
    @Insert
    suspend fun insert(item: SyncQueueEntity): Long
    
    @Update
    suspend fun update(item: SyncQueueEntity)
    
    @Query("UPDATE sync_queue SET status = :status, lastAttemptAt = :attemptAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, attemptAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE sync_queue SET retryCount = retryCount + 1, errorMessage = :error, lastAttemptAt = :attemptAt WHERE id = :id")
    suspend fun incrementRetry(id: Long, error: String, attemptAt: Long = System.currentTimeMillis())
    
    @Query("DELETE FROM sync_queue WHERE status = 'SUCCESS'")
    suspend fun clearSuccessful()
    
    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun delete(id: Long)
}
