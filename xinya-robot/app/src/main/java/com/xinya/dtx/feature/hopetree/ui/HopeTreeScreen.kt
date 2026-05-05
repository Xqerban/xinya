package com.xinya.dtx.feature.hopetree.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xinya.dtx.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HopeTreeScreen(
    onBack: () -> Unit,
    viewModel: HopeTreeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val stages = listOf(
        TreeStage(1, "种子", "入仓期", "您已播下希望的种子"),
        TreeStage(2, "发芽", "预处理期", "嫩芽破土而出"),
        TreeStage(3, "幼苗", "预处理期", "小苗茁壮成长"),
        TreeStage(4, "小树", "移植期", "新生命注入力量"),
        TreeStage(5, "成长", "重建期", "枝繁叶茂"),
        TreeStage(6, "茂盛", "重建期", "郁郁葱葱"),
        TreeStage(7, "参天", "出仓期", "参天大树屹立不倒")
    )

    val infiniteTransition = rememberInfiniteTransition(label = "tree_animation")
    val swayAngle by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sway"
    )

    if (uiState.levelUpMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLevelUp() },
            title = { Text("希望之树升级了！") },
            text = { Text(uiState.levelUpMessage!!) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissLevelUp() }) {
                    Text("太棒了！", color = LeafGreen)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("希望之树") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LeafGreen,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = LeafGreen
                )
            } else if (uiState.error != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("加载失败：${uiState.error}", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.loadStatus() },
                        colors = ButtonDefaults.buttonColors(containerColor = LeafGreen)
                    ) {
                        Text("重试")
                    }
                }
            } else {
                val status = uiState.status
                val currentLevel = status?.currentLevel ?: 1
                val currentExp = status?.currentExp ?: 0
                val nextLevelExp = status?.nextLevelExp ?: 100
                val nextLevelProgress = if (nextLevelExp > 0) currentExp.toFloat() / nextLevelExp else 0f
                val safeLevel = currentLevel.coerceIn(1, stages.size)

                // 横屏双栏：左侧信息面板 + 右侧树画布
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF87CEEB),
                                    Color(0xFFE8F5E9),
                                    Color(0xFF8BC34A).copy(alpha = 0.3f)
                                )
                            )
                        )
                ) {
                    // 左栏（40%）：状态卡片 + 阶段指示器
                    Column(
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxHeight()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        // 当前状态卡片
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = 0.9f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "第 $currentLevel 阶段：${stages[safeLevel - 1].name}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = LeafGreen,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = stages[safeLevel - 1].description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "经验：$currentExp / $nextLevelExp",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = "${(nextLevelProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = LeafGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { nextLevelProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp),
                                    color = LeafGreen,
                                    trackColor = LeafGreen.copy(alpha = 0.2f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // 阶段指示器（竖向排列）
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = 0.85f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "成长历程",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = LeafGreen,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                stages.forEachIndexed { index, stage ->
                                    StageIndicatorRow(
                                        stage = index + 1,
                                        name = stage.name,
                                        clinicalPhase = stage.clinicalPhase,
                                        isCompleted = index + 1 < currentLevel,
                                        isCurrent = index + 1 == currentLevel
                                    )
                                    if (index < stages.size - 1) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                    }

                    // 右栏（60%）：树的 Canvas 动画
                    Box(
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxHeight()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawTree(currentLevel, swayAngle)
                        }
                    }
                }
            }
        }
    }
}

data class TreeStage(
    val level: Int,
    val name: String,
    val clinicalPhase: String,
    val description: String
)

@Composable
fun StageIndicatorRow(
    stage: Int,
    name: String,
    clinicalPhase: String,
    isCompleted: Boolean,
    isCurrent: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    color = when {
                        isCompleted -> LeafGreen
                        isCurrent -> EnergyOrange
                        else -> Color.LightGray
                    },
                    shape = RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stage.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) EnergyOrange else TextPrimary
            )
            Text(
                text = clinicalPhase,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

// 保留原有竖向排列的 StageIndicator（供其他地方使用）
@Composable
fun StageIndicator(
    stage: Int,
    name: String,
    isCompleted: Boolean,
    isCurrent: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = when {
                        isCompleted -> LeafGreen
                        isCurrent -> EnergyOrange
                        else -> Color.LightGray
                    },
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stage.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = if (isCurrent) EnergyOrange else TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(40.dp)
        )
    }
}

fun DrawScope.drawTree(level: Int, swayAngle: Float) {
    val centerX = size.width / 2
    val groundY = size.height * 0.85f

    drawRect(
        color = Color(0xFF8BC34A).copy(alpha = 0.3f),
        topLeft = Offset(0f, groundY),
        size = androidx.compose.ui.geometry.Size(size.width, size.height - groundY)
    )

    val trunkHeight = when (level) {
        1 -> 20f
        2 -> 50f
        3 -> 100f
        4 -> 150f
        5 -> 200f
        6 -> 250f
        7 -> 300f
        else -> 100f
    }

    val trunkWidth = trunkHeight / 8
    val crownRadius = trunkHeight / 2

    if (level >= 2) {
        val path = Path().apply {
            moveTo(centerX - trunkWidth, groundY)
            lineTo(centerX - trunkWidth / 2, groundY - trunkHeight)
            lineTo(centerX + trunkWidth / 2, groundY - trunkHeight)
            lineTo(centerX + trunkWidth, groundY)
            close()
        }
        drawPath(path, color = TreeBrown)
    }

    if (level >= 2) {
        val layers = when {
            level <= 3 -> 1
            level <= 5 -> 2
            else -> 3
        }

        for (i in 0 until layers) {
            val layerY = groundY - trunkHeight - (i * crownRadius * 0.5f)
            val layerRadius = crownRadius * (1 - i * 0.2f)
            drawCircle(
                color = LeafGreen.copy(alpha = 0.9f - i * 0.1f),
                radius = layerRadius,
                center = Offset(centerX + swayAngle * (i + 1), layerY)
            )
        }
    }

    if (level == 1) {
        drawCircle(
            color = TreeBrown,
            radius = 15f,
            center = Offset(centerX, groundY - 10f)
        )
    }
}
