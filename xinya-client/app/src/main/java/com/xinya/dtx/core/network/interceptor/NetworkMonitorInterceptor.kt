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
        if (!isNetworkAvailable()) {
            throw NoNetworkException("网络不可用，数据将在恢复连接后自动同步")
        }
        
        return chain.proceed(chain.request())
    }
    
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

class NoNetworkException(message: String) : IOException(message)
