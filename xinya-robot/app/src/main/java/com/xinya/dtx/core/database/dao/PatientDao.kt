package com.xinya.dtx.core.database.dao

import androidx.room.*
import com.xinya.dtx.core.database.entity.PatientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {
    
    @Query("SELECT * FROM patients WHERE id = :id")
    suspend fun getById(id: String): PatientEntity?
    
    @Query("SELECT * FROM patients WHERE id = :id")
    fun observeById(id: String): Flow<PatientEntity?>
    
    @Query("SELECT * FROM patients")
    fun observeAll(): Flow<List<PatientEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(patient: PatientEntity)
    
    @Update
    suspend fun update(patient: PatientEntity)
    
    @Query("UPDATE patients SET psychEnergy = :energy, updatedAt = :updatedAt WHERE id = :patientId")
    suspend fun updatePsychEnergy(patientId: String, energy: Int, updatedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE patients SET treeLevel = :level, updatedAt = :updatedAt WHERE id = :patientId")
    suspend fun updateTreeLevel(patientId: String, level: Int, updatedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE patients SET stage = :stage, updatedAt = :updatedAt WHERE id = :patientId")
    suspend fun updateStage(patientId: String, stage: String, updatedAt: Long = System.currentTimeMillis())
    
    @Delete
    suspend fun delete(patient: PatientEntity)
}
