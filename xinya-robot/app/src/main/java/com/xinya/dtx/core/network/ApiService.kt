package com.xinya.dtx.core.network

import com.xinya.dtx.core.network.dto.*
import retrofit2.Response
import retrofit2.http.*

/**
 * 心芽DTx API服务接口
 */
interface ApiService {
    
    // ==================== 机器人绑定 ====================

    @POST("api/auth/robot/bind")
    suspend fun bindRobot(@Body request: RobotBindRequest): Response<ApiResponse<RobotAuthResponse>>

    // ==================== 患者管理 ====================
    
    @POST("api/patients")
    suspend fun createPatient(@Body request: CreatePatientRequest): Response<ApiResponse<PatientDto>>
    
    @GET("api/patients/{id}")
    suspend fun getPatient(@Path("id") id: String): Response<ApiResponse<PatientDto>>
    
    // ==================== 智能体对话 ====================
    
    @POST("api/agent/chat")
    suspend fun chat(@Body request: AgentChatRequest): Response<ApiResponse<AgentChatResponse>>
    
    @GET("api/agent/recommendations")
    suspend fun getRecommendedQuestions(
        @Query("patientId") patientId: String,
        @Query("agentType") agentType: String
    ): Response<ApiResponse<RecommendedQuestionsResponse>>
    
    // ==================== 临床路径 ====================
    
    @GET("api/clinical/stage/{patientId}")
    suspend fun getCurrentStage(@Path("patientId") patientId: String): Response<ApiResponse<String>>
    
    @POST("api/clinical/transition")
    suspend fun transitionStage(@Body request: UpdateStageRequest): Response<ApiResponse<PatientDto>>
    
    // ==================== PRO数据采集 ====================

    @GET("api/pro/questions")
    suspend fun getProQuestions(
        @Query("patientId") patientId: String
    ): Response<ApiResponse<ProQuestionListDto>>

    @POST("api/pro/submit")
    suspend fun submitPro(@Body request: ProSubmitRequest): Response<ApiResponse<ProSubmitResponse>>
    
    // ==================== 希望之树 ====================
    
    @GET("api/hopetree/{patientId}")
    suspend fun getHopeTreeStatus(@Path("patientId") patientId: String): Response<ApiResponse<HopeTreeStatusResponse>>
    
    @POST("api/hopetree/grow")
    suspend fun growHopeTree(@Body request: HopeTreeGrowRequest): Response<ApiResponse<HopeTreeGrowResponse>>
    
    // ==================== 宣教内容 ====================
    
    @GET("api/education/contents")
    suspend fun getEducationContents(
        @Query("category") category: String? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20
    ): Response<ApiResponse<EducationListResponse>>
    
    // ==================== 数据同步 ====================
    
    @POST("api/sync/batch")
    suspend fun syncBatch(@Body items: List<SyncItemDto>): Response<ApiResponse<SyncResultDto>>
}

/**
 * 机器人绑定请求
 */
data class RobotBindRequest(
    val deviceId: String,
    val patientId: String,
    val bindCode: String
)

/**
 * 机器人绑定响应
 */
data class RobotAuthResponse(
    val deviceToken: String,
    val expiresIn: Long,
    val patientId: String,
    val patientName: String
)

/**
 * 同步数据项
 */
data class SyncItemDto(
    val tableName: String,
    val operation: String,
    val payload: String
)

/**
 * 同步结果
 */
data class SyncResultDto(
    val successCount: Int,
    val failedCount: Int,
    val failedItems: List<Long>
)
