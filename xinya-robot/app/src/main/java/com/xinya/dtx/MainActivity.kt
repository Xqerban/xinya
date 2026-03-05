package com.xinya.dtx

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
import com.xinya.dtx.core.session.SessionManager
import com.xinya.dtx.feature.setup.ui.SetupScreen
import com.xinya.dtx.ui.navigation.XinyaNavHost
import com.xinya.dtx.ui.theme.XinyaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * 心芽DTx主Activity
 * 作为Kiosk模式的主入口
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                            // 加载中
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        false -> {
                            // 未绑定，显示绑定界面
                            SetupScreen(onBindingSuccess = { /* isBound 变为 true 后自动切换 */ })
                        }
                        true -> {
                            // 已绑定，进入主界面
                            XinyaNavHost()
                        }
                    }
                }
            }
        }
    }
}
