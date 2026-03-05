package com.xinya.dtx.feature.home.data

import com.xinya.dtx.core.network.ApiService
import com.xinya.dtx.core.network.dto.PatientDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PatientRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun getPatient(patientId: String): Result<PatientDto> = runCatching {
        val response = apiService.getPatient(patientId)
        if (response.isSuccessful) {
            response.body()?.data ?: error("响应数据为空")
        } else {
            error("请求失败: ${response.code()}")
        }
    }
}
