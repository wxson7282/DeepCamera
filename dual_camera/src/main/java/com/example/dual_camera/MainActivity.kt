package com.example.dual_camera

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.dual_camera.ui.theme.DualCameraTheme

class MainActivity : ComponentActivity() {
    private val localContext = staticCompositionLocalOf<Context> {
        error("No context provided")
    }
    private lateinit var shutterSound: ShutterSound

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        shutterSound = ShutterSound(this)

        if (checkSelfPermission(android.Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }

        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(localContext provides this){
                DualCameraTheme {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        // 检查系统是否支持并发相机
                        if (this.packageManager
                                .hasSystemFeature(PackageManager.FEATURE_CAMERA_CONCURRENT)) {
                            ConcurrentCameraScreen(shutterSound = shutterSound)
                        } else {
                            Greeting(
                                name = "并发相机",
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                    // 显示权限授予对话框
                    if (showGrantedDialog.value) {
                        GrantedDialog(
                            onDismiss = { showGrantedDialog.value = false }
                        )
                    }
                    // 显示权限拒绝对话框
                    if (showDeniedDialog.value) {
                        DeniedDialog(
                            onDismiss = {
                                showDeniedDialog.value = false
                                finish()    // 关闭应用
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        shutterSound.release()
    }

    private val showGrantedDialog = mutableStateOf(false)
    private val showDeniedDialog = mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 权限已授予，可执行相关操作
            showGrantedDialog.value = true
        } else {
            // 权限被拒绝，可提示用户
            showDeniedDialog.value = true
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "系统不支持 $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DualCameraTheme {
        Greeting("Android")
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
        }
    )
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
        }
    )
}
