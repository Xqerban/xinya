package com.xinya.dtx.core.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xinya.dtx.MainActivity

/**
 * 开机自启动接收器
 * 确保设备重启后自动进入DTx系统
 */
class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 开机后自动启动主Activity
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(launchIntent)
        }
    }
}
