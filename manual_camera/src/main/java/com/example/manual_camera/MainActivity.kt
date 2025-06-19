package com.example.manual_camera

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.example.manual_camera.ui.theme.ManualCameraTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (checkSelfPermission(android.Manifest.permission.CAMERA) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }

        enableEdgeToEdge()
        setContent {
            ManualCameraTheme {
                Content(this)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Content(context: Context) {
    val minFocusDistance = Util.getMinFocusDistance(context)
    var stateOfZoomRatio by remember { mutableFloatStateOf(0.5f) }
    var stateOfFocusDistance by remember { mutableFloatStateOf(minFocusDistance) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Manual Camera Main") }) },
        bottomBar = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.circle),
                        tint = Color.Unspecified,
                        contentDescription = "Shutter"
                    )
                }
                Column {
                    Text(text = "Zoom Ratio: $stateOfZoomRatio")
                    Slider(
                        modifier = Modifier
                            .border(2.dp, MaterialTheme.colorScheme.primary),
                        value = stateOfZoomRatio,
                        onValueChange = { stateOfZoomRatio = it }
                    )
                    Text(text = "Focus Distance: $stateOfFocusDistance")
                    Slider(
                        modifier = Modifier
                            .border(2.dp, MaterialTheme.colorScheme.primary),
                        value = stateOfFocusDistance,
                        onValueChange = { stateOfFocusDistance = it },
                        valueRange = 0f..minFocusDistance
                    )
                }
            }
        }
    ) { innerPadding ->
        Modifier.fillMaxWidth().padding(innerPadding).CameraPreview(
            context = context,
            zoomRatio = stateOfZoomRatio,
            focusDistance = stateOfFocusDistance
        )
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

