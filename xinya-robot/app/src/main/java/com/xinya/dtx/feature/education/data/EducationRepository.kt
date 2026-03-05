package com.xinya.dtx.feature.education.data

import com.xinya.dtx.core.network.ApiService
import com.xinya.dtx.core.network.dto.EducationContentDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EducationRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun getContents(
        category: String? = null,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<List<EducationContentDto>> = runCatching {
        val response = apiService.getEducationContents(category, page, pageSize)
        if (response.isSuccessful) {
            response.body()?.data?.contents ?: emptyList()
        } else {
            error("请求失败: ${response.code()}")
        }
    }
}
