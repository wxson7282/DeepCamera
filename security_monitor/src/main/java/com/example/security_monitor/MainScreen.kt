package com.example.security_monitor

import android.content.SharedPreferences
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    sharedPreferences: SharedPreferences? = null, viewModel: StreamViewModel = viewModel()
) {

    val viewState = viewModel.viewState.value

    var mutableStateOfServerAddress by remember {
        mutableStateOf(
            sharedPreferences?.getString(
                "server_address", "192.168.1.100:8080"
            ) ?: "192.168.1.100:8080"
        )
    }

    // SurfaceView 用于渲染解码后的视频
    var surfaceView by remember { mutableStateOf<SurfaceView?>(null) }

    // 退出时释放资源
    DisposableEffect(Unit) {
        onDispose {
            viewModel.disconnect()
        }
    }

    // 连接成功后初始化解码器
    LaunchedEffect(viewState.isConnected) {
        if (viewState.isConnected && surfaceView != null) {
            viewModel.initDecoder(surfaceView!!.holder)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Security Monitor" + if (viewState.isConnected) " · 已连接" else ""
                    )
                })
        }) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // 视频显示区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f), contentAlignment = Alignment.Center
            ) {
                if (viewState.isConnected) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                surfaceView = this
                            }
                        }, modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = viewState.errorMessage ?: "未连接",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 连接控制区域
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // IP 地址输入
                val serverAddress = mutableStateOfServerAddress
                OutlinedTextField(
                    value = serverAddress,
                    onValueChange = {
                        mutableStateOfServerAddress = it
                    },
                    label = { Text("服务器地址") },
                    enabled = !viewState.isConnected,
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                // 连接/断开按钮
                Button(
                    onClick = {
                        if (viewState.isConnected) {
                            viewModel.disconnect()
                        } else {
                            // 保存服务器地址到 SharedPreferences
                            sharedPreferences?.edit {
                                putString("server_address", serverAddress)
                            }
                            viewModel.updateServerAddress(serverAddress)
                            viewModel.connect(serverAddress)
                        }
                    },
                    colors = if (viewState.isConnected) ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE53935)
                    )
                    else ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text(if (viewState.isConnected) "断开" else "连接")
                }
            }

            // 状态信息
            if (viewState.isConnected) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "分辨率: ${viewState.videoWidth}×${viewState.videoHeight}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "编码: ${viewState.codecName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FrameRateIndicator(frameCount = viewState.frameCount)
                }
            }
        }
    }
}

@Composable
private fun FrameRateIndicator(frameCount: Int) {
    // 监听帧计数变化触发动画
    LaunchedEffect(frameCount) {
        // 当帧计数变化时触发闪烁效果
    }
    // 创建无限循环动画
    val infiniteTransition = rememberInfiniteTransition(label = "frameBlink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alphaAnimation"
    )
    Image(
        imageVector = ImageVector.vectorResource(R.drawable.video_library),
        contentDescription = "帧计数指示器",
        modifier = Modifier.size(16.dp),
        alpha = alpha,
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
            Color.Red)
    )
}

