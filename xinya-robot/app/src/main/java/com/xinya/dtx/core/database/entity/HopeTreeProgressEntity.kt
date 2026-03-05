package com.xinya.dtx.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 希望之树进度实体
 */
@Entity(tableName = "hope_tree_progress")
data class HopeTreeProgressEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val patientId: String,
    val currentLevel: Int = 1,           // 当前等级 1-7
    val currentExp: Int = 0,             // 当前经验值
    val nextLevelExp: Int = 100,         // 升级所需经验
    val lastGrowthDate: Long? = null,    // 最后一次生长时间
    val totalGrowthDays: Int = 0,        // 累计生长天数
    val updatedAt: Long = System.currentTimeMillis()
)
