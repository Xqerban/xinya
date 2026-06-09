package com.xinya.dtx.feature.home.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceDebugPanel(
    viewModel: VoiceDebugViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val aiKitPermissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add(Manifest.permission.READ_PHONE_NUMBERS)
        }
    }.toTypedArray()

    val aiKitPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) {
            viewModel.runPendingAiKitAction()
        } else {
            viewModel.onAiKitPermissionDenied()
        }
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startIat()
        } else {
            viewModel.onMicPermissionDenied()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "离线语音联调",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "状态：${uiState.status}",
                style = MaterialTheme.typography.bodyMedium
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.queueAiKitAction(VoiceDebugAiKitAction.TestTts)
                        aiKitPermissionLauncher.launch(aiKitPermissions)
                    }
                ) {
                    Text("测 TTS")
                }
                Button(
                    onClick = {
                        viewModel.queueAiKitAction(VoiceDebugAiKitAction.TestWakeupReply)
                        aiKitPermissionLauncher.launch(aiKitPermissions)
                    }
                ) {
                    Text("测我在")
                }
                Button(
                    onClick = {
                        viewModel.queueAiKitAction(VoiceDebugAiKitAction.StartWakeup)
                        aiKitPermissionLauncher.launch(aiKitPermissions)
                    }
                ) {
                    Text("测唤醒")
                }
                Button(
                    onClick = {
                        viewModel.queueAiKitAction(VoiceDebugAiKitAction.StartCommands)
                        aiKitPermissionLauncher.launch(aiKitPermissions)
                    }
                ) {
                    Text("测命令词")
                }
                Button(
                    onClick = {
                        viewModel.queueAiKitAction(VoiceDebugAiKitAction.StartFullVoice)
                        aiKitPermissionLauncher.launch(aiKitPermissions)
                    }
                ) {
                    Text("完整语音")
                }
                Button(onClick = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Text("测听写")
                }
                OutlinedButton(onClick = { viewModel.stopAll() }) {
                    Text("全部停止")
                }
                OutlinedButton(onClick = { viewModel.clearLogs() }) {
                    Text("清日志")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                uiState.logs.forEach { line ->
                    val isError = line.contains("错误") || line.contains("error", ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isError) Color(0xFFFFEAEA) else Color(0xFFF6F6F6),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isError) Color(0xFF1F1F1F) else Color(0xFF111111)
                        )
                    }
                }
            }
        }
    }
}
