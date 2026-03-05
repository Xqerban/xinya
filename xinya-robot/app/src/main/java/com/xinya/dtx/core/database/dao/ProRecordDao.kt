package com.xinya.dtx.core.database.dao

import androidx.room.*
import com.xinya.dtx.core.database.entity.ProRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProRecordDao {
    
    @Query("SELECT * FROM pro_records WHERE patientId = :patientId ORDER BY recordDate DESC")
    fun observeByPatient(patientId: String): Flow<List<ProRecordEntity>>
    
    @Query("SELECT * FROM pro_records WHERE patientId = :patientId AND recordDate = :date")
    suspend fun getByDate(patientId: String, date: Long): List<ProRecordEntity>
    
    @Query("SELECT * FROM pro_records WHERE isSynced = 0")
    suspend fun getUnsynced(): List<ProRecordEntity>
    
    @Insert
    suspend fun insert(record: ProRecordEntity): Long
    
    @Insert
    suspend fun insertAll(records: List<ProRecordEntity>)
    
    @Query("UPDATE pro_records SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Long)
}
