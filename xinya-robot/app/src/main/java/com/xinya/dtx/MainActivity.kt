package com.xinya.dtx

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.robotemi.sdk.Robot
import com.robotemi.sdk.listeners.OnRobotReadyListener
import com.xinya.dtx.core.session.SessionManager
import com.xinya.dtx.feature.setup.ui.SetupScreen
import com.xinya.dtx.ui.navigation.XinyaNavHost
import com.xinya.dtx.ui.theme.XinyaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * 心芽DTx主Activity
 * 作为 temi Kiosk 模式的主入口，集成 temi SDK OnRobotReadyListener
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity(), OnRobotReadyListener {

    @Inject
    lateinit var sessionManager: SessionManager

    private lateinit var robot: Robot

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        robot = Robot.getInstance()
        enableEdgeToEdge()
        setContent {
            XinyaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isBound by sessionManager.isBound.collectAsState(initial = null)

                    when (isBound) {
                        null -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        false -> {
                            SetupScreen(onBindingSuccess = { })
                        }
                        true -> {
                            XinyaNavHost()
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        robot.addOnRobotReadyListener(this)
    }

    override fun onStop() {
        super.onStop()
        robot.removeOnRobotReadyListener(this)
    }

    /**
     * temi SDK 就绪回调
     * 调用 robot.onStart() 使应用图标出现在 temi 顶部导航栏，并完成技能注册
     */
    override fun onRobotReady(isReady: Boolean) {
        if (isReady) {
            try {
                val activityInfo = packageManager.getActivityInfo(
                    componentName,
                    PackageManager.GET_META_DATA
                )
                robot.onStart(activityInfo)
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }
        }
    }
}
