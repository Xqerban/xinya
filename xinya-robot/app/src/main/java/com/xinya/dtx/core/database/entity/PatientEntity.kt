package com.xinya.dtx.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.xinya.dtx.clinical.ClinicalStage

/**
 * 患者信息实体
 */
@Entity(tableName = "patients")
data class PatientEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val stage: ClinicalStage,
    val psychEnergy: Int = 50,           // 心理能量值 0-100
    val treeLevel: Int = 1,              // 希望之树等级 1-7
    val admissionDate: Long,             // 入仓日期时间戳
    val roomNumber: String? = null,      // 病房号
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
