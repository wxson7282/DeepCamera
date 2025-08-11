package com.example.manual_camera

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.manual_camera.ui.theme.ManualCameraTheme

class MainActivity : ComponentActivity() {

    private val localContext = staticCompositionLocalOf<Context> {
        error("No context provided")
    }
    private lateinit var shutterSound: ShutterSound
    private val showCameraGrantedDialog = mutableStateOf(false)
    private val showCameraDeniedDialog = mutableStateOf(false)
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 相机权限已授予，检查写入存储权限
            showCameraGrantedDialog.value = true
        } else {
            // 权限被拒绝，可提示用户
            showCameraDeniedDialog.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        shutterSound = ShutterSound(this)

        if (checkSelfPermission(android.Manifest.permission.CAMERA)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }

        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(localContext provides this) {
                ManualCameraTheme {
                    ManualCameraScreen(shutterSound)
                    // 显示相机权限授予对话框
                    if (showCameraGrantedDialog.value) {
                        GrantedDialog(
                            onDismiss = { showCameraGrantedDialog.value = false })
                    }
                    // 显示相机权限拒绝对话框
                    if (showCameraDeniedDialog.value) {
                        DeniedDialog(
                            onDismiss = {
                                showCameraDeniedDialog.value = false
                                finish()    // 关闭应用
                            })
                    }
                }
            }
        }
    }
}

@Composable
private fun GrantedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("权限授予") },
        text = { Text("相机权限已成功授予，可以使用相机功能。") },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("确定")
            }
        })
}

@Composable
private fun DeniedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("权限拒绝") },
        text = { Text("相机权限被拒绝，可能无法使用相机功能。") },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("确定")
            }
        })
}
