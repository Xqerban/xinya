package com.xinya.dtx.core.kiosk

import android.app.Activity
import android.content.Context
import android.view.WindowManager
import com.robotemi.sdk.Robot
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kiosk 模式管理器（temi SDK 版本）
 *
 * 在 temi 上 Kiosk 模式由 temi OS 统一管理：
 * - 通过 AndroidManifest 中声明 com.robotemi.sdk.metadata.KIOSK 注册为 Kiosk 技能
 * - 通过 requestToBeKioskApp() 动态申请成为当前选中的主屏幕技能
 * - temi 的顶部栏显示/隐藏通过 robot.hideTopBar() / robot.showTopBar() 控制
 */
@Singleton
class KioskManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val robot: Robot get() = Robot.getInstance()

    private var isKioskModeEnabled = false

    /**
     * 启用 Kiosk 模式
     * 1. 申请成为 temi 选中的 Kiosk 技能（会弹出系统确认对话框）
     * 2. 隐藏 temi 顶部导航栏
     * 3. 保持屏幕常亮
     */
    fun enableKioskMode(activity: Activity) {
        isKioskModeEnabled = true

        // 保持屏幕常亮
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 申请成为选中的 temi Kiosk 技能（如尚未选中）
        if (!robot.isSelectedKioskApp()) {
            robot.requestToBeKioskApp()
        }

        // 隐藏 temi 顶部导航栏，实现沉浸式体验
        robot.hideTopBar()
    }

    /**
     * 禁用 Kiosk 模式
     */
    fun disableKioskMode(activity: Activity) {
        isKioskModeEnabled = false

        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 恢复显示 temi 顶部导航栏
        robot.showTopBar()
    }

    /**
     * 检查是否处于 Kiosk 模式（本地状态 + temi OS 状态双重判断）
     */
    fun isKioskModeActive(): Boolean {
        return isKioskModeEnabled || robot.isSelectedKioskApp()
    }

    /**
     * 处理返回键（在 Kiosk 模式下拦截，防止用户退出）
     */
    fun handleBackPress(): Boolean {
        return isKioskModeActive()
    }
}
