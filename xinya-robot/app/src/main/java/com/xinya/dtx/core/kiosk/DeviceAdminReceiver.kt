package com.xinya.dtx.core.kiosk

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * 设备管理员接收器
 * 用于Kiosk模式的设备管理功能
 */
class DeviceAdminReceiver : DeviceAdminReceiver() {
    
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        // 设备管理员已启用
    }
    
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        // 设备管理员已禁用
    }
}
