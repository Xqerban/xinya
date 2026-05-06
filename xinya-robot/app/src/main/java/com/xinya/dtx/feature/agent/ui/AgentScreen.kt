package com.xinya.dtx.feature.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xinya.dtx.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(
    agentType: String,
    onBack: () -> Unit,
    viewModel: AgentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPsychAgent = agentType == "psych"
    val agentName = if (isPsychAgent) "小芽" else "小护士"
    val agentColor = if (isPsychAgent) XiaoyaGreen else NurseBlue
    val agentBackground = if (isPsychAgent) XiaoyaBackground else NurseBackground

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(agentType) {
        viewModel.initialize(agentType)
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }

    if (uiState.crisisAlert) {
        AlertDialog(
            onDismissRequest = { },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red) },
            title = { Text("心理预警") },
            text = { Text("系统检测到您可能需要帮助，已通知您的主治医生，请保持冷静。") },
            confirmButton = {
                TextButton(onClick = { }) {
                    Text("好的", color = PrimaryGreen)
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.padding(top = TemiTopBarHeight),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(agentColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = agentName.first().toString(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = agentName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isPsychAgent) "心理陪护伙伴" else "护理宣教伙伴",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = agentColor,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        // 横屏：整体聊天区域居中，限制最大宽度
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(agentBackground),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 900.dp)
                    .fillMaxHeight()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(uiState.messages) { message ->
                        if (message.isLoading && message.content.isEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(agentColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        agentName.first().toString(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Card(
                                    shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = agentColor,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "正在思考...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        } else {
                            ChatBubble(message = message, agentColor = agentColor, agentName = agentName)
                        }
                    }

                    if (uiState.recommendedQuestions.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                Text(
                                    text = "您可能想问：",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                uiState.recommendedQuestions.forEach { question ->
                                    SuggestionChip(
                                        onClick = { viewModel.sendMessage(agentType, question) },
                                        label = { Text(question) },
                                        modifier = Modifier.padding(end = 8.dp, bottom = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.error != null) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text(
                                    text = "发送失败: ${uiState.error}",
                                    modifier = Modifier.padding(12.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                // 语音输入区域
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // 状态提示文字
                        Text(
                            text = when {
                                uiState.isListening -> "正在聆听..."
                                uiState.isSending -> "正在思考..."
                                else -> "点击麦克风开始说话"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                uiState.isListening -> agentColor
                                uiState.isSending -> Color.Gray
                                else -> Color.Gray
                            },
                            modifier = Modifier.weight(1f)
                        )

                        // 大麦克风按钮
                        IconButton(
                            onClick = { viewModel.startVoiceInput() },
                            enabled = !uiState.isSending && !uiState.isListening,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(
                                    when {
                                        uiState.isListening -> agentColor
                                        uiState.isSending -> Color.LightGray
                                        else -> agentColor.copy(alpha = 0.12f)
                                    }
                                )
                        ) {
                            Icon(
                                imageVector = if (uiState.isListening) Icons.Filled.Mic else Icons.Filled.MicNone,
                                contentDescription = if (uiState.isListening) "正在聆听" else "点击说话",
                                tint = when {
                                    uiState.isListening -> Color.White
                                    uiState.isSending -> Color.White
                                    else -> agentColor
                                },
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessageUi, agentColor: Color, agentName: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isFromUser) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(agentColor),
                contentAlignment = Alignment.Center
            ) {
                Text(agentName.first().toString(), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Card(
            modifier = Modifier.widthIn(max = 420.dp),
            shape = if (message.isFromUser)
                RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
            else
                RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isFromUser) agentColor else Color.White
            )
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.isFromUser) Color.White else TextPrimary
            )
        }
    }
}
