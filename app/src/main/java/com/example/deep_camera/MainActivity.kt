package com.example.deep_camera

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.deep_camera.ui.theme.DeepCameraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(android.Manifest.permission.CAMERA) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
        enableEdgeToEdge()
        setContent {
            DeepCameraTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
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
fun GrantedDialog(onDismiss: () -> Unit) {
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
fun DeniedDialog(onDismiss: () -> Unit) {
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


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DeepCameraTheme {
        Greeting("Android")
    }
}