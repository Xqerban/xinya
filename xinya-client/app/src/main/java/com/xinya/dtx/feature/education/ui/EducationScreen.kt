package com.xinya.dtx.feature.education.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xinya.dtx.ui.theme.*

/**
 * 护理学堂 - 宣教内容展示
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EducationScreen(
    onBack: () -> Unit
) {
    // 模拟数据
    val categories = listOf("全部", "入仓准备", "预处理", "移植", "康复护理", "饮食指导")
    var selectedCategory by remember { mutableStateOf("全部") }
    
    val videos = remember {
        listOf(
            EducationVideo("1", "认识骨髓移植", "入仓准备", "了解骨髓移植的基本流程", 320, true),
            EducationVideo("2", "预处理期护理要点", "预处理", "预处理期间的注意事项", 450, true),
            EducationVideo("3", "感染预防指南", "康复护理", "如何有效预防感染", 280, false),
            EducationVideo("4", "口腔护理技巧", "康复护理", "保持口腔健康的方法", 200, false),
            EducationVideo("5", "饮食禁忌须知", "饮食指导", "移植期间的饮食注意事项", 360, false),
            EducationVideo("6", "情绪调节方法", "康复护理", "保持积极心态的技巧", 400, false)
        )
    }
    
    val filteredVideos = if (selectedCategory == "全部") videos 
                         else videos.filter { it.category == selectedCategory }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("护理学堂") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EnergyOrange,
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
            // 学习进度卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "学习进度",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "已完成 2/6 个视频",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { 2f / 6f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = EnergyOrange,
                            trackColor = EnergyOrangeLight.copy(alpha = 0.3f)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "33%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = EnergyOrange
                    )
                }
            }
            
            // 分类标签
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EnergyOrange,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 视频列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredVideos) { video ->
                    VideoCard(video = video)
                }
            }
        }
    }
}

data class EducationVideo(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val durationSeconds: Int,
    val isCompleted: Boolean
)

@Composable
fun VideoCard(video: EducationVideo) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO: 播放视频 */ },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 视频缩略图占位
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (video.isCompleted) LeafGreen.copy(alpha = 0.2f)
                        else EnergyOrange.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (video.isCompleted) Icons.Default.CheckCircle else Icons.Default.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = if (video.isCompleted) LeafGreen else EnergyOrange
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (video.isCompleted) {
                        Text(
                            text = "已学习",
                            style = MaterialTheme.typography.labelSmall,
                            color = LeafGreen,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = video.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = { },
                        label = { Text(video.category, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatDuration(video.durationSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "${minutes}:${secs.toString().padStart(2, '0')}"
}
