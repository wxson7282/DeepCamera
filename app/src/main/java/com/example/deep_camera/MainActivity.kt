package com.example.deep_camera

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.deep_camera.Util.getMinFocusDistance
import com.example.deep_camera.ui.theme.DeepCameraTheme

class MainActivity : ComponentActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private val localContext = staticCompositionLocalOf<Context> {
        error("No context provided")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = getSharedPreferences("DeepCamera", MODE_PRIVATE)
        if (checkSelfPermission(android.Manifest.permission.CAMERA) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }

        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(localContext provides this) {
                DeepCameraTheme {
                    InitFocusDistanceList()
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppNavigation()
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

    @Composable
    fun AppNavigation() {
        Log.i("AppNavigation", "start")
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = "main") {
            composable("main") {
                MainSurface(
                    navController = navController,
                    sharedPreferences = sharedPreferences
                )
            }
            composable("settings") {
                SettingsSurface(
                    navController = navController,
                    sharedPreferences = sharedPreferences
                )
            }
        }
    }

    @Composable
    fun InitFocusDistanceList() {
        val localMinFocusDistance = getMinFocusDistance(this)
        val mutableStateFocusList = remember {
            mutableStateListOf<FocusItem>(
                *(Util.loadFocusArray(sharedPreferences) ?: defaultFocusArray)
            )
        }
        // 检查mutableStateFocusList中的最大值和最小值
        val maxValue = mutableStateFocusList.maxOf { it.focusAt }
        val minValue = mutableStateFocusList.minOf { it.focusAt }
        // 如果最大值不等于localMinFocusDistance或最小值不等于0.0f,则重新初始化mutableStateFocusList
        // 并保存到sharedPreferences
        if (maxValue != localMinFocusDistance || minValue!= 0.0f) {
            mutableStateFocusList.clear()
            mutableStateFocusList.addAll(Util.initFocusArray(localMinFocusDistance))
            sharedPreferences.let {
                Util.saveFocusArray(it, mutableStateFocusList.toTypedArray())
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

@Preview(showBackground = true)
@Composable
fun MainActivityPreview() {
    DeepCameraTheme {
        SettingsSurface()
    }
}
