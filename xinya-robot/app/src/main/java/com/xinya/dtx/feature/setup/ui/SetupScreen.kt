package com.xinya.dtx.feature.setup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xinya.dtx.ui.theme.*

/**
 * 机器人首次启动绑定患者的设置界面
 *
 * 流程：
 * 1. 护士在管理端（PAD）为患者生成6位绑定码
 * 2. 在此界面输入患者ID + 绑定码完成绑定
 * 3. 绑定成功后跳转到主界面
 */
@Composable
fun SetupScreen(
    onBindingSuccess: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var patientId by remember { mutableStateOf("") }
    var bindCode by remember { mutableStateOf("") }

    // 绑定成功后自动跳转
    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onBindingSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(PrimaryGreen, LeafGreen)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo
                Icon(
                    imageVector = Icons.Default.MedicalServices,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = PrimaryGreen
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "心芽 DTx",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryGreen
                )

                Text(
                    text = "数字疗法机器人端",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "绑定患者",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "请让护士在管理端生成绑定码，\n然后在下方输入患者ID和绑定码",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 患者ID输入框
                OutlinedTextField(
                    value = patientId,
                    onValueChange = {
                        patientId = it
                        viewModel.clearError()
                    },
                    label = { Text("患者ID") },
                    placeholder = { Text("例如：p-001") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryGreen,
                        focusedLabelColor = PrimaryGreen
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 绑定码输入框
                OutlinedTextField(
                    value = bindCode,
                    onValueChange = {
                        if (it.length <= 6) {
                            bindCode = it.uppercase()
                            viewModel.clearError()
                        }
                    },
                    label = { Text("6位绑定码") },
                    placeholder = { Text("输入护士提供的绑定码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryGreen,
                        focusedLabelColor = PrimaryGreen
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                )

                // 错误提示
                if (uiState.error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 绑定按钮
                Button(
                    onClick = { viewModel.bind(patientId.trim(), bindCode.trim()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                    enabled = !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("绑定中...")
                    } else {
                        Text(
                            "开始绑定",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 开发调试快捷按钮（可在正式版中移除）
                TextButton(
                    onClick = {
                        patientId = "p-001"
                        bindCode = "123456"
                    }
                ) {
                    Text(
                        "【调试】使用默认参数",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
