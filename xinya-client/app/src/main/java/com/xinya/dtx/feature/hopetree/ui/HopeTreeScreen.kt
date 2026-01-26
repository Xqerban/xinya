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
import com.xinya.dtx.ui.theme.*

/**
 * 希望之树 - 游戏化生长系统
 * 将患者的免疫重建过程可视化为树的生长
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HopeTreeScreen(
    onBack: () -> Unit
) {
    // 模拟数据 - 当前生长等级 (1-7)
    val currentLevel = 3
    val currentEnergy = 75
    val nextLevelProgress = 0.6f
    
    val stages = listOf(
        TreeStage(1, "种子", "入仓期", "您已播下希望的种子"),
        TreeStage(2, "发芽", "预处理期", "嫩芽破土而出"),
        TreeStage(3, "幼苗", "预处理期", "小苗茁壮成长"),
        TreeStage(4, "小树", "移植期", "新生命注入力量"),
        TreeStage(5, "成长", "重建期", "枝繁叶茂"),
        TreeStage(6, "茂盛", "重建期", "郁郁葱葱"),
        TreeStage(7, "参天", "出仓期", "参天大树屹立不倒")
    )
    
    // 动画
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF87CEEB), // 天空蓝
                            Color(0xFFE8F5E9), // 浅绿
                            Color(0xFF8BC34A).copy(alpha = 0.3f) // 草地
                        )
                    )
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // 当前状态卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "第 $currentLevel 阶段：${stages[currentLevel - 1].name}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = LeafGreen
                    )
                    Text(
                        text = stages[currentLevel - 1].description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 进度条
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "下一阶段进度",
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
                        Spacer(modifier = Modifier.height(4.dp))
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
            }
            
            // 树的可视化区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    drawTree(currentLevel, swayAngle)
                }
            }
            
            // 能量值显示
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "心理能量",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "能量越高，树长得越快",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Text(
                        text = "$currentEnergy",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = EnergyOrange
                    )
                }
            }
            
            // 生长阶段指示器
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                stages.forEachIndexed { index, stage ->
                    StageIndicator(
                        stage = index + 1,
                        name = stage.name,
                        isCompleted = index + 1 < currentLevel,
                        isCurrent = index + 1 == currentLevel
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
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
fun StageIndicator(
    stage: Int,
    name: String,
    isCompleted: Boolean,
    isCurrent: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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

/**
 * 绘制树的简化版本
 */
fun DrawScope.drawTree(level: Int, swayAngle: Float) {
    val centerX = size.width / 2
    val groundY = size.height * 0.85f
    
    // 绘制地面
    drawRect(
        color = Color(0xFF8BC34A).copy(alpha = 0.3f),
        topLeft = Offset(0f, groundY),
        size = androidx.compose.ui.geometry.Size(size.width, size.height - groundY)
    )
    
    // 根据等级绘制不同大小的树
    val trunkHeight = when (level) {
        1 -> 20f  // 种子
        2 -> 50f  // 发芽
        3 -> 100f // 幼苗
        4 -> 150f // 小树
        5 -> 200f // 成长
        6 -> 250f // 茂盛
        7 -> 300f // 参天
        else -> 100f
    }
    
    val trunkWidth = trunkHeight / 8
    val crownRadius = trunkHeight / 2
    
    // 绘制树干
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
    
    // 绘制树冠
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
    
    // 种子阶段只画一个点
    if (level == 1) {
        drawCircle(
            color = TreeBrown,
            radius = 15f,
            center = Offset(centerX, groundY - 10f)
        )
    }
}
