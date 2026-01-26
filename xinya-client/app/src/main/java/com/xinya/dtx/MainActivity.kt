package com.xinya.dtx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.xinya.dtx.ui.theme.XinyaTheme
import com.xinya.dtx.ui.navigation.XinyaNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * 心芽DTx主Activity
 * 作为Kiosk模式的主入口
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XinyaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    XinyaNavHost()
                }
            }
        }
    }
}
