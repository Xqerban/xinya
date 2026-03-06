package com.xinya.dtx.core.network.dto

/**
 * 宣教内容DTO
 */
data class EducationContentDto(
    val id: String,
    val title: String,
    val stage: String? = null,
    val category: String,
    val description: String,
    val contentType: String,            // "video" | "article"
    val durationSeconds: Int = 0,
    val thumbnailUrl: String? = null,
    val mediaUrl: String? = null,
    val tags: List<String> = emptyList(),
    val sortOrder: Int = 0,
    val isActive: Boolean = true
)

/**
 * 宣教内容列表响应（对应后端 PageResult<EducationContentDto>）
 */
data class EducationListResponse(
    val list: List<EducationContentDto>,
    val total: Long,
    val page: Int,
    val pageSize: Int
)

/**
 * 观看进度上报请求
 */
data class EducationProgressRequest(
    val patientId: String,
    val contentId: String,
    val watchedSeconds: Int,
    val completed: Boolean
)

/**
 * 观看进度上报响应
 */
data class EducationProgressResponse(
    val hopeTreeExpDelta: Int,
    val completionRate: Double
)
