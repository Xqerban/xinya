package com.xinya.dtx.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * PRO数据采集记录实体
 */
@Entity(tableName = "pro_records")
data class ProRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val patientId: String,
    val recordDate: Long,                // 记录日期
    val questionId: String,              // 问题ID
    val questionTitle: String,           // 问题标题
    val answer: String,                  // 答案
    val answerScore: Int = 0,            // 答案分数（用于分析）
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
