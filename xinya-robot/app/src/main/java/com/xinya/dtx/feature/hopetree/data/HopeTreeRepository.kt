package com.xinya.dtx.feature.hopetree.data

import com.xinya.dtx.core.network.ApiService
import com.xinya.dtx.core.network.dto.HopeTreeGrowRequest
import com.xinya.dtx.core.network.dto.HopeTreeGrowResponse
import com.xinya.dtx.core.network.dto.HopeTreeStatusResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HopeTreeRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun getStatus(patientId: String): Result<HopeTreeStatusResponse> = runCatching {
        val response = apiService.getHopeTreeStatus(patientId)
        if (response.isSuccessful) {
            response.body()?.data ?: error("响应数据为空")
        } else {
            error("请求失败: ${response.code()}")
        }
    }

    suspend fun grow(
        patientId: String,
        growthSource: String,
        expAmount: Int
    ): Result<HopeTreeGrowResponse> = runCatching {
        val request = HopeTreeGrowRequest(
            patientId = patientId,
            growthSource = growthSource,
            expAmount = expAmount
        )
        val response = apiService.growHopeTree(request)
        if (response.isSuccessful) {
            response.body()?.data ?: error("响应数据为空")
        } else {
            error("请求失败: ${response.code()}")
        }
    }
}
