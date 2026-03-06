package com.xinya.dtx.feature.education.data

import com.xinya.dtx.core.network.ApiService
import com.xinya.dtx.core.network.dto.EducationContentDto
import com.xinya.dtx.core.network.dto.EducationProgressRequest
import com.xinya.dtx.core.network.dto.EducationProgressResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EducationRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun getContents(
        stage: String? = null,
        category: String? = null,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<List<EducationContentDto>> = runCatching {
        val response = apiService.getEducationContents(
            stage = stage,
            category = category,
            page = page,
            pageSize = pageSize
        )
        if (response.isSuccessful) {
            response.body()?.data?.list ?: emptyList()
        } else {
            error("请求失败: ${response.code()}")
        }
    }

    suspend fun getContentDetail(id: String): Result<EducationContentDto> = runCatching {
        val response = apiService.getEducationContent(id)
        if (response.isSuccessful) {
            response.body()?.data ?: error("内容不存在")
        } else {
            error("请求失败: ${response.code()}")
        }
    }

    suspend fun reportProgress(
        patientId: String,
        contentId: String,
        watchedSeconds: Int,
        completed: Boolean
    ): Result<EducationProgressResponse> = runCatching {
        val response = apiService.reportProgress(
            EducationProgressRequest(patientId, contentId, watchedSeconds, completed)
        )
        if (response.isSuccessful) {
            response.body()?.data ?: error("上报失败")
        } else {
            error("请求失败: ${response.code()}")
        }
    }
}
