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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xinya.dtx.ui.theme.*

/**
 * 双智能体对话界面
 * 支持小芽(心理陪护)和小护士(护理宣教)两种模式
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(
    agentType: String,
    onBack: () -> Unit
) {
    val isPsychAgent = agentType == "psych"
    val agentName = if (isPsychAgent) "小芽" else "小护士"
    val agentColor = if (isPsychAgent) XiaoyaGreen else NurseBlue
    val agentBackground = if (isPsychAgent) XiaoyaBackground else NurseBackground
    
    var inputText by remember { mutableStateOf("") }
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                content = if (isPsychAgent) 
                    "您好，我是小芽，您的心理陪护伙伴。今天感觉怎么样？有什么想和我聊聊的吗？" 
                else 
                    "您好，我是小护士，您的护理宣教伙伴。有什么关于护理方面的问题想要了解吗？",
                isFromUser = false
            )
        )
    }
    val listState = rememberLazyListState()
    
    // 推荐问题
    val recommendedQuestions = if (isPsychAgent) {
        listOf("今天心情有些低落", "感觉有点焦虑", "想做个深呼吸练习")
    } else {
        listOf("预处理期需要注意什么？", "如何预防感染？", "饮食有什么禁忌？")
    }
    
    Scaffold(
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(agentBackground)
        ) {
            // 消息列表
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(messages) { message ->
                    ChatBubble(
                        message = message,
                        agentColor = agentColor
                    )
                }
                
                // 推荐问题
                if (messages.size <= 2) {
                    item {
                        Column(
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text(
                                text = "您可能想问：",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            recommendedQuestions.forEach { question ->
                                SuggestionChip(
                                    onClick = {
                                        messages.add(ChatMessage(question, true))
                                        // 模拟回复
                                        messages.add(ChatMessage(
                                            "这是一个很好的问题。作为Demo，这里返回默认回复。实际接入AI后将提供个性化的回答。",
                                            false
                                        ))
                                    },
                                    label = { Text(question) },
                                    modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            // 输入区域
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 语音按钮
                    IconButton(
                        onClick = { /* TODO: 语音输入 */ }
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "语音输入",
                            tint = agentColor
                        )
                    }
                    
                    // 文本输入框
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入您想说的话...") },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = agentColor,
                            unfocusedBorderColor = Color.LightGray
                        ),
                        maxLines = 3
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // 发送按钮
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                messages.add(ChatMessage(inputText.trim(), true))
                                inputText = ""
                                // 模拟回复
                                messages.add(ChatMessage(
                                    "收到您的消息。作为Demo，这里返回默认回复。实际接入AI后将提供个性化的回答。",
                                    false
                                ))
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(agentColor)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

data class ChatMessage(
    val content: String,
    val isFromUser: Boolean
)

@Composable
fun ChatBubble(
    message: ChatMessage,
    agentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isFromUser) 16.dp else 4.dp,
                bottomEnd = if (message.isFromUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isFromUser) agentColor else Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                color = if (message.isFromUser) Color.White else TextPrimary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
