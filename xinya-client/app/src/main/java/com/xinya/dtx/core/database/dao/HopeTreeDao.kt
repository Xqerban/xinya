package com.xinya.dtx.core.database.dao

import androidx.room.*
import com.xinya.dtx.core.database.entity.HopeTreeProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HopeTreeDao {
    
    @Query("SELECT * FROM hope_tree_progress WHERE patientId = :patientId")
    suspend fun getByPatient(patientId: String): HopeTreeProgressEntity?
    
    @Query("SELECT * FROM hope_tree_progress WHERE patientId = :patientId")
    fun observeByPatient(patientId: String): Flow<HopeTreeProgressEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(progress: HopeTreeProgressEntity)
    
    @Update
    suspend fun update(progress: HopeTreeProgressEntity)
    
    @Query("UPDATE hope_tree_progress SET currentLevel = :level, currentExp = :exp, updatedAt = :updatedAt WHERE patientId = :patientId")
    suspend fun updateProgress(patientId: String, level: Int, exp: Int, updatedAt: Long = System.currentTimeMillis())
}
