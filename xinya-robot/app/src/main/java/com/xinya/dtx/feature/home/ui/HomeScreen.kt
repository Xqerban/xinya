package com.xinya.dtx.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xinya.dtx.BuildConfig
import com.xinya.dtx.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAgent: (String) -> Unit,
    onNavigateToHopeTree: () -> Unit,
    onNavigateToEducation: () -> Unit,
    onNavigateToPro: () -> Unit,
    onNavigateToMeditation: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.padding(top = TemiTopBarHeight),
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
        // 横屏双栏布局：左侧欢迎卡 + 右侧功能网格
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BackgroundCream)
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 左栏（35%）：欢迎卡片
            Box(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    WelcomeCard(uiState)
                    if (BuildConfig.DEBUG) {
                        VoiceDebugPanel()
                    }
                }
            }

            // 右栏（65%）：3 列功能入口网格
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .weight(0.65f)
                    .fillMaxHeight()
            ) {
                item {
                    FeatureCard(
                        title = "小芽",
                        subtitle = "心理陪护伙伴",
                        icon = Icons.Default.Favorite,
                        backgroundColor = XiaoyaGreen,
                        onClick = { onNavigateToAgent("psych") }
                    )
                }
                item {
                    FeatureCard(
                        title = "小护士",
                        subtitle = "护理宣教伙伴",
                        icon = Icons.Default.LocalHospital,
                        backgroundColor = NurseBlue,
                        onClick = { onNavigateToAgent("nurse") }
                    )
                }
                item {
                    FeatureCard(
                        title = "希望之树",
                        subtitle = "见证您的康复",
                        icon = Icons.Default.Park,
                        backgroundColor = LeafGreen,
                        onClick = onNavigateToHopeTree
                    )
                }
                item {
                    FeatureCard(
                        title = "护理学堂",
                        subtitle = "专业知识学习",
                        icon = Icons.Default.MenuBook,
                        backgroundColor = EnergyOrange,
                        onClick = onNavigateToEducation
                    )
                }
                item {
                    FeatureCard(
                        title = "每日打卡",
                        subtitle = "记录康复点滴",
                        icon = Icons.Default.CheckCircle,
                        backgroundColor = PrimaryGreen,
                        onClick = onNavigateToPro
                    )
                }
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
fun WelcomeCard(uiState: HomeUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryGreen)
            }
        } else {
            val patient = uiState.patient
            val energyFraction = (patient?.psychEnergy ?: 75) / 100f
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 头像占位圆圈
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(PrimaryGreen.copy(alpha = 0.15f), RoundedCornerShape(36.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = PrimaryGreen
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "您好，${patient?.name ?: "勇敢的战士"}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "当前阶段：${patient?.stage ?: "--"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                LinearProgressIndicator(
                    progress = { energyFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = EnergyOrange,
                    trackColor = EnergyOrangeLight.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "心理能量: ${patient?.psychEnergy ?: "--"}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = EnergyOrange,
                    fontWeight = FontWeight.Bold
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
            .aspectRatio(1.2f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(backgroundColor, backgroundColor.copy(alpha = 0.8f))
                    )
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(52.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.height(10.dp))
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
