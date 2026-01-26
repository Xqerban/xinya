package com.xinya.dtx.feature.pro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
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
 * PRO数据采集 - 每日打卡
 * 支持图文点击和语音问答两种方式
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProScreen(
    onBack: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val totalSteps = 5
    
    val questions = listOf(
        ProQuestion("mood", "今天的心情怎么样？", listOf("很好", "还行", "一般", "不太好", "很差")),
        ProQuestion("sleep", "昨晚睡眠质量如何？", listOf("很好", "还行", "一般", "较差", "失眠")),
        ProQuestion("appetite", "今天的食欲如何？", listOf("很好", "正常", "一般", "较差", "没有食欲")),
        ProQuestion("pain", "是否有疼痛不适？", listOf("没有", "轻微", "中等", "较重", "严重")),
        ProQuestion("nausea", "是否有恶心呕吐？", listOf("没有", "轻微恶心", "明显恶心", "偶尔呕吐", "频繁呕吐"))
    )
    
    val answers = remember { mutableStateMapOf<String, String>() }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("每日打卡") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryGreen,
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
                .background(BackgroundCream)
        ) {
            // 进度指示
            LinearProgressIndicator(
                progress = { (currentStep + 1).toFloat() / totalSteps },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = PrimaryGreen,
                trackColor = PrimaryGreen.copy(alpha = 0.2f)
            )
            
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    // 日期显示
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "2026年1月26日",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "入仓第7天 · 预处理期",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Text(
                                text = "${currentStep + 1}/$totalSteps",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryGreen
                            )
                        }
                    }
                }
                
                item {
                    // 当前问题
                    val question = questions[currentStep]
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Text(
                                text = question.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            // 选项列表
                            question.options.forEachIndexed { index, option ->
                                val isSelected = answers[question.id] == option
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) PrimaryGreen.copy(alpha = 0.1f)
                                            else Color.Transparent
                                        )
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) PrimaryGreen else Color.LightGray,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            answers[question.id] = option
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) PrimaryGreen else Color.Transparent
                                            )
                                            .border(
                                                width = 2.dp,
                                                color = if (isSelected) PrimaryGreen else Color.LightGray,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.width(12.dp))
                                    
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isSelected) PrimaryGreen else TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // 语音输入提示
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NurseBackground)
                                    .clickable { /* TODO: 语音输入 */ }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = NurseBlue
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "点击使用语音回答",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NurseBlue
                                )
                            }
                        }
                    }
                }
            }
            
            // 底部按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentStep > 0) {
                    OutlinedButton(
                        onClick = { currentStep-- },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("上一题")
                    }
                }
                
                Button(
                    onClick = {
                        if (currentStep < totalSteps - 1) {
                            currentStep++
                        } else {
                            // 提交
                            onBack()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                    enabled = answers.containsKey(questions[currentStep].id)
                ) {
                    Text(if (currentStep < totalSteps - 1) "下一题" else "提交")
                }
            }
        }
    }
}

data class ProQuestion(
    val id: String,
    val title: String,
    val options: List<String>
)
