package com.xinya.dtx.core.network.dto

import com.google.gson.annotations.SerializedName

/**
 * 通用API响应包装
 */
data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T?
)

/**
 * 分页请求
 */
data class PageRequest(
    val page: Int = 1,
    @SerializedName("page_size")
    val pageSize: Int = 20
)

/**
 * 分页响应
 */
data class PageResponse<T>(
    val items: List<T>,
    val total: Int,
    val page: Int,
    @SerializedName("page_size")
    val pageSize: Int,
    @SerializedName("total_pages")
    val totalPages: Int
)
