package com.example.security_camera

import android.content.SharedPreferences
import android.util.Log
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MainSurface(
    modifier: Modifier = Modifier,
    clickable: Clickable = combinedClickable(),
    sharedPreferences: SharedPreferences
) {
    Log.i("DisplaySurface", "DisplaySurface start")
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewState = viewModel<SecurityCameraViewModel>().viewState.value
    // 初始化相机管理器
    val cameraManager = remember {
        CameraManager(
            context = context,
            lifecycleOwner = lifecycleOwner,
            sharedPreferences = sharedPreferences
        )
    }

    // 相机初始化
    LaunchedEffect(Unit) {
        cameraManager.initCamera()
    }

    // 生命周期管理
    DisposableEffect(Unit) {
        onDispose {
            cameraManager.release()
        }
    }

    ConstraintLayout(
        modifier = Modifier.fillMaxSize()
    ) {
        val (previewRef, buttonRowRef, videoCaptureBtnRef, screenBtnRef) = createRefs()
        // 预览视图
        AndroidView(factory = { cameraManager.previewView }, modifier = Modifier.constrainAs(previewRef) {
            top.linkTo(parent.top)
            bottom.linkTo(parent.bottom)
            start.linkTo(parent.start)
            end.linkTo(parent.end)
        })
        // 操作按钮行
        Row(modifier = Modifier
            .constrainAs(buttonRowRef) {
                bottom.linkTo(parent.bottom)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }
            .fillMaxWidth()) {
            val mutableInteractionSource = remember { MutableInteractionSource() }
            val isPressed = mutableInteractionSource.collectIsPressedAsState().value
            // 定义录像按钮
            IconButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    // 录像
                    cameraManager.recordVideos()
                }) {
                Icon(
                    imageVector = if (viewState.isVideoRecoding)
                        ImageVector.vectorResource(R.drawable.baseline_square_24)
                    else
                        ImageVector.vectorResource(R.drawable.baseline_circle_24),
                    contentDescription = "Record"
                )
            }
        }
    }
}

data class Clickable constructor(
    val onRecord : () -> Unit,
    val onScreenOff : () -> Unit
)

fun combinedClickable(
    onRecord : () -> Unit = {},
    onScreenOff : () -> Unit = {}
) = Clickable(onRecord, onScreenOff)
