package com.xinya.dtx.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.xinya.dtx.core.database.converter.Converters
import com.xinya.dtx.core.database.dao.*
import com.xinya.dtx.core.database.entity.*

/**
 * 心芽DTx本地数据库
 * 用于离线优先架构的数据持久化
 */
@Database(
    entities = [
        PatientEntity::class,
        ConversationEntity::class,
        ProRecordEntity::class,
        HopeTreeProgressEntity::class,
        EducationProgressEntity::class,
        SyncQueueEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun patientDao(): PatientDao
    abstract fun conversationDao(): ConversationDao
    abstract fun proRecordDao(): ProRecordDao
    abstract fun hopeTreeDao(): HopeTreeDao
    abstract fun syncQueueDao(): SyncQueueDao
    
    companion object {
        const val DATABASE_NAME = "xinya_dtx_db"
    }
}
