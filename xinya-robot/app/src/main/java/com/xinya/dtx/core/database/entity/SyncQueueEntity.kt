package com.xinya.dtx.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 离线同步队列实体
 * 用于存储待同步的数据
 */
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tableName: String,               // 源表名
    val recordId: Long,                  // 源记录ID
    val operation: String,               // "INSERT" | "UPDATE" | "DELETE"
    val payload: String,                 // JSON格式的数据
    val retryCount: Int = 0,             // 重试次数
    val maxRetries: Int = 3,             // 最大重试次数
    val status: String = "PENDING",      // "PENDING" | "SYNCING" | "FAILED" | "SUCCESS"
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAttemptAt: Long? = null
)
