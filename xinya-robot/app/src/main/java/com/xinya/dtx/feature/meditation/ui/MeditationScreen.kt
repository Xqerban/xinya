package com.xinya.dtx.feature.meditation.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xinya.dtx.ui.theme.*

/**
 * 冥想空间 - 横屏适配版
 * 列表页：2 列 Grid；播放页：左侧呼吸圆圈 + 右侧控制区
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeditationScreen(
    onBack: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(false) }
    var selectedMeditation by remember { mutableStateOf<MeditationItem?>(null) }

    val meditations = listOf(
        MeditationItem("1", "生命奠基礼", "入仓仪式", "为您的康复之旅播下希望的种子", 600, Color(0xFF4CAF50)),
        MeditationItem("2", "生命之河", "深度放松", "让生命的河流洗涤身心的疲惫", 900, Color(0xFF2196F3)),
        MeditationItem("3", "内在黎明", "晨间唤醒", "迎接新一天的希望与力量", 480, Color(0xFFFF9800)),
        MeditationItem("4", "宁静港湾", "睡前放松", "在平静中进入甜美的梦乡", 720, Color(0xFF673AB7)),
        MeditationItem("5", "呼吸练习", "焦虑缓解", "通过呼吸找回内心的平静", 300, Color(0xFF00BCD4)),
        MeditationItem("6", "身体扫描", "疼痛管理", "觉察身体，释放紧张", 600, Color(0xFFE91E63))
    )

    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    Scaffold(
        modifier = Modifier.padding(top = TemiTopBarHeight),
        topBar = {
            TopAppBar(
                title = { Text("冥想空间") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF673AB7),
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
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A237E),
                            Color(0xFF311B92),
                            Color(0xFF4A148C)
                        )
                    )
                )
        ) {
            if (selectedMeditation != null && isPlaying) {
                // 播放界面：横屏 Row 布局（左侧呼吸圆圈 + 右侧控制区）
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(40.dp),
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧：呼吸圆圈动画
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(260.dp)
                                .scale(breathScale)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            selectedMeditation!!.color.copy(alpha = 0.6f),
                                            selectedMeditation!!.color.copy(alpha = 0.2f),
                                            Color.Transparent
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (breathScale > 1.15f) "吸气" else "呼气",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 右侧：标题、进度、控制按钮
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = selectedMeditation!!.title,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = selectedMeditation!!.category,
                            style = MaterialTheme.typography.titleMedium,
                            color = selectedMeditation!!.color
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "深呼吸，放松身心...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.8f)
                        )

                        Spacer(modifier = Modifier.height(40.dp))

                        // 进度条
                        LinearProgressIndicator(
                            progress = { 0.3f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "3:00",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = formatMeditationDuration(selectedMeditation!!.durationSeconds),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // 控制按钮
                        IconButton(
                            onClick = {
                                isPlaying = false
                                selectedMeditation = null
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Icon(
                                Icons.Default.Pause,
                                contentDescription = "暂停",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            } else {
                // 列表界面：2 列 Grid
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = "选择一个冥想练习",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(meditations) { meditation ->
                            MeditationCard(
                                meditation = meditation,
                                onClick = {
                                    selectedMeditation = meditation
                                    isPlaying = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

data class MeditationItem(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val durationSeconds: Int,
    val color: Color
)

@Composable
fun MeditationCard(
    meditation: MeditationItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(meditation.color.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = meditation.color,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meditation.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = meditation.category,
                    style = MaterialTheme.typography.labelMedium,
                    color = meditation.color
                )
                Text(
                    text = meditation.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            Text(
                text = formatMeditationDuration(meditation.durationSeconds),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

fun formatMeditationDuration(seconds: Int): String {
    val minutes = seconds / 60
    return "${minutes}分钟"
}
