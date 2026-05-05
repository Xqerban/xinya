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
import com.xinya.dtx.core.network.dto.ProQuestionDto
import com.xinya.dtx.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProScreen(
    onBack: () -> Unit,
    viewModel: ProViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))

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
        // 横屏：整体居中，限制最大宽度
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BackgroundCream),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxHeight()
            ) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = PrimaryGreen
                        )
                    }

                    uiState.alreadyCheckedIn -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(96.dp),
                                tint = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "今日已打卡",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "您今天已经完成了每日打卡，明天再来吧！",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = onBack,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                            ) {
                                Text("返回首页")
                            }
                        }
                    }

                    uiState.isSubmitted -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(96.dp),
                                tint = PrimaryGreen
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "打卡成功！",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryGreen
                            )
                            if (uiState.energyDelta > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "心理能量 +${uiState.energyDelta}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = EnergyOrange
                                )
                            }
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = onBack,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                            ) {
                                Text("返回首页")
                            }
                        }
                    }

                    uiState.questions.isEmpty() && uiState.error != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "加载失败：${uiState.error}",
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                            ) { Text("重试") }
                        }
                    }

                    uiState.questions.isNotEmpty() -> {
                        val totalSteps = uiState.questions.size
                        val currentStep = uiState.currentStep
                        val question = uiState.questions[currentStep]

                        Column(modifier = Modifier.fillMaxSize()) {
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
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                item {
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
                                            Text(
                                                text = today,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
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
                                    QuestionCard(
                                        question = question,
                                        currentAnswer = uiState.answers[question.id],
                                        onSelectOption = { answer, score ->
                                            viewModel.selectAnswer(question.id, answer, score)
                                        }
                                    )

                                    if (uiState.error != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = uiState.error!!,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            // 底部按钮
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White)
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (currentStep > 0) {
                                    OutlinedButton(
                                        onClick = { viewModel.prevStep() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("上一题")
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (currentStep < totalSteps - 1) {
                                            viewModel.nextStep()
                                        } else {
                                            viewModel.submit()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                                    enabled = uiState.answers.containsKey(question.id)
                                            && !uiState.isSubmitting
                                ) {
                                    if (uiState.isSubmitting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text(if (currentStep < totalSteps - 1) "下一题" else "提交")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuestionCard(
    question: ProQuestionDto,
    currentAnswer: Pair<String, Int>?,
    onSelectOption: (answer: String, score: Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = question.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(24.dp))

            when (question.type) {
                "scale" -> {
                    val minVal = (question.min ?: 1).toFloat()
                    val maxVal = (question.max ?: 10).toFloat()
                    val currentScore = currentAnswer?.second?.toFloat() ?: minVal

                    Text(
                        text = "当前：${currentScore.toInt()} 分",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryGreen,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = currentScore,
                        onValueChange = { score ->
                            onSelectOption(score.toInt().toString(), score.toInt())
                        },
                        valueRange = minVal..maxVal,
                        steps = (maxVal - minVal).toInt() - 1,
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryGreen,
                            activeTrackColor = PrimaryGreen
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = question.minLabel ?: minVal.toInt().toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = question.maxLabel ?: maxVal.toInt().toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                else -> {
                    question.options?.forEach { option ->
                        val isSelected = currentAnswer?.first == option.value

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
                                .clickable { onSelectOption(option.value, option.score) }
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
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) PrimaryGreen else TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}
