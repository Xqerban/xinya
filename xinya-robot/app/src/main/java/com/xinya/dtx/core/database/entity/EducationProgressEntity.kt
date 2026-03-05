package com.xinya.dtx.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 宣教学习进度实体
 */
@Entity(tableName = "education_progress")
data class EducationProgressEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val patientId: String,
    val contentId: String,               // 内容ID
    val contentType: String,             // "video" | "article"
    val isCompleted: Boolean = false,
    val watchDuration: Int = 0,          // 观看时长（秒）
    val totalDuration: Int = 0,          // 总时长
    val lastWatchPosition: Int = 0,      // 上次观看位置
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
