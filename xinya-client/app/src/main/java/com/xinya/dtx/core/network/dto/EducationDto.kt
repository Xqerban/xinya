package com.xinya.dtx.core.network.dto

import com.google.gson.annotations.SerializedName

/**
 * 宣教内容DTO
 */
data class EducationContentDto(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    @SerializedName("content_type")
    val contentType: String,            // "video" | "article"
    @SerializedName("duration_seconds")
    val durationSeconds: Int,
    @SerializedName("thumbnail_url")
    val thumbnailUrl: String?,
    @SerializedName("media_url")
    val mediaUrl: String?,
    val tags: List<String> = emptyList()
)

/**
 * 宣教内容列表响应
 */
data class EducationListResponse(
    val contents: List<EducationContentDto>,
    val total: Int
)
