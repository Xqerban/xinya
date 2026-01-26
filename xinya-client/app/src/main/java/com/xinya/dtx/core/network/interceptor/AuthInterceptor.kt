package com.xinya.dtx.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * 认证拦截器
 * 自动添加认证头信息
 */
class AuthInterceptor @Inject constructor() : Interceptor {
    
    // TODO: 从安全存储中获取token
    private var authToken: String? = null
    
    fun setToken(token: String) {
        authToken = token
    }
    
    fun clearToken() {
        authToken = null
    }
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        val request = if (authToken != null) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $authToken")
                .header("Content-Type", "application/json")
                .build()
        } else {
            originalRequest.newBuilder()
                .header("Content-Type", "application/json")
                .build()
        }
        
        return chain.proceed(request)
    }
}
