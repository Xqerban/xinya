package com.xinya.dtx.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 对话记录实体
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val patientId: String,
    val agentType: String,               // "psych" | "nurse"
    val sessionId: String,
    val message: String,
    val isFromUser: Boolean,
    val psychEnergyDelta: Int = 0,       // 心理能量变化
    val crisisAlert: Boolean = false,    // 危机干预标识
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false        // 是否已同步到服务器
)
