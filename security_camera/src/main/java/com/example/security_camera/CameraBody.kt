package com.example.security_camera

import android.content.SharedPreferences
import androidx.camera.view.PreviewView
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CameraBody(
    modifier: Modifier = Modifier,
    clickable: Clickable = combinedClickable(),
    sharedPreferences: SharedPreferences,
    previewView: PreviewView,
) {
    val viewState = viewModel<SecurityCameraViewModel>().viewState.value
    ConstraintLayout(
        modifier = Modifier.fillMaxSize()
    ) {
        val (previewRef, buttonRowRef, videoCaptureBtnRef, screenBtnRef) = createRefs()
        // 预览视图
        AndroidView(factory = { previewView }, modifier = Modifier.constrainAs(previewRef) {
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
                    clickable.onRecordBtnPressed()
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