package com.xinya.dtx.core.network.interceptor

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

/**
 * 网络监控拦截器
 * 检测网络状态，支持弱网环境下的降级处理
 */
class NetworkMonitorInterceptor @Inject constructor(
    @ApplicationContext private val context: Context
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        // 网络不可用时直接放行，让 OkHttp 抛出 IOException，避免误判拦截
        return chain.proceed(chain.request())
    }
    
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        // 只检查是否有网络接口，不检查 VALIDATED（避免模拟器/内网环境误判）
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

class NoNetworkException(message: String) : IOException(message)
