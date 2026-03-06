@file:OptIn(ExperimentalMaterial3Api::class)

package com.xinya.dtx.feature.education.ui

import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.xinya.dtx.core.network.dto.EducationContentDto
import com.xinya.dtx.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun EducationDetailScreen(
    onBack: () -> Unit,
    viewModel: EducationDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 完成提示
    if (uiState.showCompletionToast) {
        LaunchedEffect(Unit) {
            delay(3000)
            viewModel.dismissCompletionToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.content?.title ?: "宣教内容",
                        maxLines = 1
                    )
                },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BackgroundCream)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = EnergyOrange
                    )
                }

                uiState.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "加载失败：${uiState.error}",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = EnergyOrange)
                        ) {
                            Text("返回")
                        }
                    }
                }

                uiState.content != null -> {
                    val content = uiState.content!!
                    if (content.contentType == "video") {
                        VideoDetailContent(
                            content = content,
                            isCompleted = uiState.isCompleted,
                            isReporting = uiState.isReporting,
                            onMarkCompleted = { watchedSeconds ->
                                viewModel.markAsCompleted(watchedSeconds)
                            }
                        )
                    } else {
                        ArticleDetailContent(
                            content = content,
                            isCompleted = uiState.isCompleted,
                            isReporting = uiState.isReporting,
                            onMarkCompleted = { viewModel.markAsCompleted(0) }
                        )
                    }
                }
            }

            // 完成奖励浮层提示
            AnimatedVisibility(
                visible = uiState.showCompletionToast,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = EnergyOrange)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color.White)
                        Text(
                            text = "学习完成！希望之树 +${uiState.rewardExp} 经验",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ========== 视频详情页 ==========

@OptIn(UnstableApi::class)
@Composable
private fun VideoDetailContent(
    content: EducationContentDto,
    isCompleted: Boolean,
    isReporting: Boolean,
    onMarkCompleted: (Int) -> Unit
) {
    val context = LocalContext.current
    var watchedSeconds by remember { mutableIntStateOf(0) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            if (!content.mediaUrl.isNullOrBlank()) {
                setMediaItem(MediaItem.fromUri(content.mediaUrl))
                prepare()
            }
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        watchedSeconds = (duration / 1000).toInt().coerceAtLeast(0)
                    }
                }
            })
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    // 定时更新观看秒数
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(1000)
            if (exoPlayer.isPlaying) {
                watchedSeconds = (exoPlayer.currentPosition / 1000).toInt()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 视频播放器
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        )

        ContentInfoSection(
            content = content,
            isCompleted = isCompleted,
            isReporting = isReporting,
            onMarkCompleted = { onMarkCompleted(watchedSeconds) }
        )
    }
}

// ========== 文章详情页 ==========

@Composable
private fun ArticleDetailContent(
    content: EducationContentDto,
    isCompleted: Boolean,
    isReporting: Boolean,
    onMarkCompleted: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 封面占位图
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(EnergyOrange.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Article,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = EnergyOrange.copy(alpha = 0.5f)
            )
        }

        ContentInfoSection(
            content = content,
            isCompleted = isCompleted,
            isReporting = isReporting,
            onMarkCompleted = onMarkCompleted
        )
    }
}

// ========== 公共信息区块 ==========

@Composable
private fun ContentInfoSection(
    content: EducationContentDto,
    isCompleted: Boolean,
    isReporting: Boolean,
    onMarkCompleted: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // 标题
        Text(
            text = content.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 分类 + 时长
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = EnergyOrange.copy(alpha = 0.15f)
            ) {
                Text(
                    text = content.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = EnergyOrange,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            if (content.durationSeconds > 0) {
                Icon(
                    imageVector = if (content.contentType == "video")
                        Icons.Default.PlayCircle else Icons.Default.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = TextSecondary
                )
                val minutes = content.durationSeconds / 60
                val seconds = content.durationSeconds % 60
                Text(
                    text = if (content.contentType == "video")
                        "%02d:%02d".format(minutes, seconds)
                    else
                        "$minutes 分钟阅读",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = Color(0xFFEEEEEE))
        Spacer(modifier = Modifier.height(16.dp))

        // 描述
        Text(
            text = content.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
        )

        // 标签
        if (content.tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                content.tags.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFF0F4F8)
                    ) {
                        Text(
                            text = "# $tag",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 完成按钮
        Button(
            onClick = onMarkCompleted,
            enabled = !isCompleted && !isReporting,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isCompleted) Color(0xFF4CAF50) else EnergyOrange,
                disabledContainerColor = if (isCompleted) Color(0xFF4CAF50) else EnergyOrange.copy(alpha = 0.5f)
            )
        ) {
            if (isReporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isCompleted) "已完成学习" else "标记为已学完",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
