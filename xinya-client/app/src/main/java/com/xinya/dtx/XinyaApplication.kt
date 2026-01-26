package com.xinya.dtx

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * 心芽DTx应用程序入口
 * 骨髓移植隔离病房数字疗法系统
 */
@HiltAndroidApp
class XinyaApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // 初始化逻辑将在后续完善
    }
}
