package com.xinya.dtx.core.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kiosk模式管理器
 * 医疗级锁定：屏蔽系统层、开机自启、异常自恢复
 */
@Singleton
class KioskManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private var isKioskModeEnabled = false
    
    /**
     * 启用Kiosk模式
     */
    fun enableKioskMode(activity: Activity) {
        isKioskModeEnabled = true
        
        // 1. 全屏沉浸模式
        enableImmersiveMode(activity)
        
        // 2. 保持屏幕常亮
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // 3. 锁定任务模式（需要设备管理员权限）
        startLockTask(activity)
    }
    
    /**
     * 禁用Kiosk模式
     */
    fun disableKioskMode(activity: Activity) {
        isKioskModeEnabled = false
        
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stopLockTask(activity)
    }
    
    /**
     * 启用沉浸式全屏模式
     */
    private fun enableImmersiveMode(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
    }
    
    /**
     * 启动任务锁定模式
     */
    private fun startLockTask(activity: Activity) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, DeviceAdminReceiver::class.java)
            
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                // 设备所有者模式 - 完全锁定
                dpm.setLockTaskPackages(adminComponent, arrayOf(context.packageName))
                activity.startLockTask()
            } else {
                // 普通模式 - 尝试锁定（需要用户确认）
                activity.startLockTask()
            }
        } catch (e: Exception) {
            // 锁定失败，记录日志但不影响使用
            e.printStackTrace()
        }
    }
    
    /**
     * 停止任务锁定模式
     */
    private fun stopLockTask(activity: Activity) {
        try {
            activity.stopLockTask()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 检查是否处于Kiosk模式
     */
    fun isKioskModeActive(): Boolean = isKioskModeEnabled
    
    /**
     * 处理返回键（在Kiosk模式下拦截）
     */
    fun handleBackPress(): Boolean {
        return isKioskModeEnabled
    }
}
