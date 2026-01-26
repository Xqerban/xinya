package com.xinya.dtx.core.sync

import android.content.Context
import androidx.work.*
import com.google.gson.Gson
import com.xinya.dtx.core.database.dao.SyncQueueDao
import com.xinya.dtx.core.database.entity.SyncQueueEntity
import com.xinya.dtx.core.network.ApiService
import com.xinya.dtx.core.network.SyncItemDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 离线同步管理器
 * 实现Local-First架构，确保弱网环境下的数据可靠性
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncQueueDao: SyncQueueDao,
    private val apiService: ApiService,
    private val gson: Gson
) {
    
    companion object {
        private const val SYNC_WORK_NAME = "xinya_data_sync"
        private const val SYNC_INTERVAL_MINUTES = 15L
    }
    
    /**
     * 初始化定时同步任务
     */
    fun initializePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
    }
    
    /**
     * 立即触发同步
     */
    fun triggerImmediateSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()
        
        WorkManager.getInstance(context)
            .enqueue(syncRequest)
    }
    
    /**
     * 将数据加入同步队列
     */
    suspend fun enqueue(
        tableName: String,
        recordId: Long,
        operation: String,
        payload: Any
    ) {
        val entity = SyncQueueEntity(
            tableName = tableName,
            recordId = recordId,
            operation = operation,
            payload = gson.toJson(payload)
        )
        syncQueueDao.insert(entity)
    }
    
    /**
     * 观察待同步数量
     */
    fun observePendingCount(): Flow<Int> {
        return syncQueueDao.observePendingCount()
    }
    
    /**
     * 执行同步
     */
    suspend fun performSync(): SyncResult {
        val pendingItems = syncQueueDao.getPending()
        
        if (pendingItems.isEmpty()) {
            return SyncResult(0, 0, emptyList())
        }
        
        var successCount = 0
        var failedCount = 0
        val failedIds = mutableListOf<Long>()
        
        // 批量同步
        try {
            val syncItems = pendingItems.map { item ->
                SyncItemDto(
                    tableName = item.tableName,
                    operation = item.operation,
                    payload = item.payload
                )
            }
            
            val response = apiService.syncBatch(syncItems)
            
            if (response.isSuccessful && response.body()?.data != null) {
                val result = response.body()!!.data!!
                successCount = result.successCount
                failedCount = result.failedCount
                failedIds.addAll(result.failedItems)
                
                // 标记成功的项目
                pendingItems.filter { it.id !in failedIds }.forEach { item ->
                    syncQueueDao.updateStatus(item.id, "SUCCESS")
                }
                
                // 更新失败项目的重试次数
                pendingItems.filter { it.id in failedIds }.forEach { item ->
                    if (item.retryCount < item.maxRetries) {
                        syncQueueDao.incrementRetry(item.id, "同步失败")
                    } else {
                        syncQueueDao.updateStatus(item.id, "FAILED")
                    }
                }
            }
        } catch (e: Exception) {
            // 网络异常，所有项目保持待处理状态
            failedCount = pendingItems.size
            pendingItems.forEach { item ->
                syncQueueDao.incrementRetry(item.id, e.message ?: "未知错误")
            }
        }
        
        // 清理已成功的项目
        syncQueueDao.clearSuccessful()
        
        return SyncResult(successCount, failedCount, failedIds)
    }
}

/**
 * 同步结果
 */
data class SyncResult(
    val successCount: Int,
    val failedCount: Int,
    val failedIds: List<Long>
)

/**
 * 同步Worker
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    // TODO: 使用Hilt注入SyncManager
    
    override suspend fun doWork(): Result {
        // 实际实现中需要注入SyncManager并调用performSync()
        return Result.success()
    }
}
