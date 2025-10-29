package com.example.security_camera

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DisplaySurface(
    modifier: Modifier = Modifier
) {
    Log.i("DisplaySurface", "DisplaySurface start")
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val securityCameraViewModel: SecurityCameraViewModel = viewModel()
    val viewState = securityCameraViewModel.viewState.value
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val preview = remember { Util.getPreview() }
    val videoCapture = remember { Util.getVideoCapture() }

    LaunchedEffect(cameraProviderFuture) {
        // 监听CameraProvider的变化，从监听器中获取CameraProvider实例
        cameraProvider = cameraProviderFuture.get()
    }

    DisposableEffect(lifecycleOwner, cameraProvider) {
        val cameraProvider = cameraProvider ?: return@DisposableEffect onDispose {}
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                videoCapture,
                preview
            )
        } catch (e: IllegalStateException) {
            Log.e(
                "DisplaySurface",
                "the use case has already been bound to another lifecycle or method is not called on main thread",
                e
            )
        } catch (e: IllegalArgumentException) {
            Log.e(
                "DisplaySurface",
                "the provided camera selector is unable to resolve a camera to be used for the given use cases",
                e
            )
        } catch (e: Exception) {
            Log.e("DisplaySurface", "Error binding camera use cases", e)
            e.printStackTrace()
        }

        onDispose {
            cameraProvider.unbindAll()
        }
    }

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
                    // 录像
                    Util.recordVideos(
                        context = context, videoCapture = videoCapture, camera = camera!!
                    )
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