package com.xinya.dtx.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xinya.dtx.ui.theme.*

/**
 * 心之港湾 - 主页
 * 患者的数字疗法入口
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAgent: (String) -> Unit,
    onNavigateToHopeTree: () -> Unit,
    onNavigateToEducation: () -> Unit,
    onNavigateToPro: () -> Unit,
    onNavigateToMeditation: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "心之港湾",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BackgroundCream)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 欢迎语
            WelcomeCard()
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 功能入口网格
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                // 小芽 - 心理陪护
                item {
                    FeatureCard(
                        title = "小芽",
                        subtitle = "心理陪护伙伴",
                        icon = Icons.Default.Favorite,
                        backgroundColor = XiaoyaGreen,
                        onClick = { onNavigateToAgent("psych") }
                    )
                }
                
                // 小护士 - 护理宣教
                item {
                    FeatureCard(
                        title = "小护士",
                        subtitle = "护理宣教伙伴",
                        icon = Icons.Default.LocalHospital,
                        backgroundColor = NurseBlue,
                        onClick = { onNavigateToAgent("nurse") }
                    )
                }
                
                // 希望之树
                item {
                    FeatureCard(
                        title = "希望之树",
                        subtitle = "见证您的康复",
                        icon = Icons.Default.Park,
                        backgroundColor = LeafGreen,
                        onClick = onNavigateToHopeTree
                    )
                }
                
                // 护理学堂
                item {
                    FeatureCard(
                        title = "护理学堂",
                        subtitle = "专业知识学习",
                        icon = Icons.Default.MenuBook,
                        backgroundColor = EnergyOrange,
                        onClick = onNavigateToEducation
                    )
                }
                
                // 每日打卡
                item {
                    FeatureCard(
                        title = "每日打卡",
                        subtitle = "记录康复点滴",
                        icon = Icons.Default.CheckCircle,
                        backgroundColor = PrimaryGreen,
                        onClick = onNavigateToPro
                    )
                }
                
                // 冥想空间
                item {
                    FeatureCard(
                        title = "冥想空间",
                        subtitle = "内心的宁静",
                        icon = Icons.Default.SelfImprovement,
                        backgroundColor = Color(0xFF9C27B0),
                        onClick = onNavigateToMeditation
                    )
                }
            }
        }
    }
}

@Composable
fun WelcomeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "您好，勇敢的战士",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "今天是您入仓的第 7 天",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                // 心理能量条
                LinearProgressIndicator(
                    progress = { 0.75f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = EnergyOrange,
                    trackColor = EnergyOrangeLight.copy(alpha = 0.3f)
                )
                Text(
                    text = "心理能量: 75%",
                    style = MaterialTheme.typography.labelMedium,
                    color = EnergyOrange,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun FeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            backgroundColor,
                            backgroundColor.copy(alpha = 0.8f)
                        )
                    )
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(48.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
