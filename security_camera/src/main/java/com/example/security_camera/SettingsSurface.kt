package com.example.security_camera

import android.content.SharedPreferences
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSurface(
    navController: NavController? = null,
    sharedPreferences: SharedPreferences? = null,
    myCameraManager: MyCameraManager
) {
    val logTag = "SettingsSurface"
    // 输入对话框是否打开
    val openDialog4VideoClipLength = remember { mutableStateOf(false) }
    val openDialog4StorageSpace = remember { mutableStateOf(false) }
    // 视频片段长度，以分钟为单位
    var mutableStateOfVideoClipLength by remember {
        mutableIntStateOf(
            sharedPreferences?.getInt(
                "video_clip_length",
                5
            ) ?: 5
        )
    }
    // 存储空间，以GB为单位
    var mutableStateOfStorageSpace by remember {
        mutableIntStateOf(
            sharedPreferences?.getInt(
                "storage_space",
                5
            ) ?: 5
        )
    }
    // 视频质量格式
    val videoQualityOptions = listOf("SD", "HD", "FHD")
    var mutableVideoQuality by remember {
        mutableStateOf(
            sharedPreferences?.getString(
                "video_quality",
                videoQualityOptions[0]
            ) ?: videoQualityOptions[0]
        )
    }
    // 视频帧率
    var mutableFps by remember {
        mutableStateOf(
            sharedPreferences?.getString("video_fps", "30-30") ?: "30-30"
        )
    }

    // 启动异步任务
    LaunchedEffect(Unit) {
        // 初始化相机
        myCameraManager.initCamera()
        Log.i(logTag, "cameraManager.initCamera()")
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Security Camera Settings") }, navigationIcon = {
            IconButton(
                modifier = Modifier.background(MaterialTheme.colorScheme.primary), onClick = {
                    navigateToMain(navController)
                }) {
                Icon(
                    imageVector = Icons.Outlined.Home, contentDescription = "Home"
                )
            }
        })
    }, bottomBar = {
        BottomAppBar {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    modifier = Modifier.background(MaterialTheme.colorScheme.primary),
                    onClick = {
                        sharedPreferences?.edit {
                            if (mutableStateOfVideoClipLength in 1..10) {
                                putInt("video_clip_length", mutableStateOfVideoClipLength)
                                openDialog4VideoClipLength.value = false
                            } else {
                                openDialog4VideoClipLength.value = true
                                return@edit
                            }

                            if (mutableStateOfStorageSpace in 1..128) {
                                putInt("storage_space", mutableStateOfStorageSpace)
                                openDialog4StorageSpace.value = false
                            } else {
                                openDialog4StorageSpace.value = true
                                return@edit
                            }
                            putString("video_quality", mutableVideoQuality)
                            putString("video_fps", mutableFps)
                            navigateToMain(navController)
                        }
                    }) {
                    Icon(
                        imageVector = Icons.Filled.Done, contentDescription = "Done"
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    modifier = Modifier.background(MaterialTheme.colorScheme.primary),
                    onClick = { navigateToMain(navController) }) {
                    Icon(
                        imageVector = Icons.Filled.Close, contentDescription = "Close"
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val videoClipLength = mutableStateOfVideoClipLength.toString()
                OutlinedTextField(
                    modifier = Modifier.padding(2.dp),
                    label = { Text("视频片段长度（1-10分钟）") },
                    value = videoClipLength,
                    onValueChange = { newValue ->
                        mutableStateOfVideoClipLength = newValue.toIntOrNull() ?: 0
                    })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val storageSpace = mutableStateOfStorageSpace.toString()
                OutlinedTextField(
                    modifier = Modifier.padding(2.dp),
                    label = { Text("存储空间（1-128GB）") },
                    value = storageSpace,
                    onValueChange = { newValue ->
                        mutableStateOfStorageSpace = newValue.toIntOrNull() ?: 0
                    })
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                videoQualityOptions.forEach { text ->
                    Column(
                        modifier = Modifier
                            .padding(2.dp)
                            .selectable(
                                selected = (mutableVideoQuality == text),
                                onClick = { mutableVideoQuality = text },
                                role = Role.RadioButton
                            )
                    ) {
                        RadioButton(
                            selected = (mutableVideoQuality == text), onClick = null
                        )
                        Text(text)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // FPS选择下拉框
                val fps = mutableFps
                val fpsOptions = myCameraManager.getFpsRanges()
                var expanded by remember { mutableStateOf(false) }
                // 显示当前选中的FPS范围
                val currentFpsText = remember(fps) {
                    val foundRange =fpsOptions.find { (it.lower.toString() + "-" + it.upper.toString()) == fps }
                    foundRange?.let {"${it.lower}-${it.upper}" } ?: fps
                }
                // 下拉菜单触发按钮
                OutlinedTextField(
                    value = currentFpsText,
                    onValueChange = {},
                    label = { Text("视频帧率") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = "展开FPS选项")
                        }
                    })

                DropdownMenu(
                    modifier = Modifier.offset(x = 40.dp, y = 0.dp),
                    expanded = expanded,
                    onDismissRequest = { expanded = false }) {
                    fpsOptions.forEach { fpsRange ->
                        val text = fpsRange.lower.toString() + "-" + fpsRange.upper.toString()
                        DropdownMenuItem(onClick = {
                            mutableFps = text
                            expanded = false
                        }, text = { Text(text) })
                    }
                }
            }
        }
    }

    if (openDialog4VideoClipLength.value) {
        AlertDialog(
            onDismissRequest = { openDialog4VideoClipLength.value = false },
            title = { Text("Error") },
            text = { Text("Please input a number between 1 and 10") },
            confirmButton = {
                IconButton(onClick = { openDialog4VideoClipLength.value = false }) {
                    Icon(Icons.Filled.Done, contentDescription = "OK")
                }
            })
    }
    if (openDialog4StorageSpace.value) {
        AlertDialog(
            onDismissRequest = { openDialog4StorageSpace.value = false },
            title = { Text("Error") },
            text = { Text("Please input a number between 1 and 128") },
            confirmButton = {
                IconButton(onClick = { openDialog4StorageSpace.value = false }) {
                    Icon(Icons.Filled.Done, contentDescription = "OK")
                }
            })
    }
}

private fun navigateToMain(navController: NavController?) {
    navController?.navigate("main") {
        popUpTo("settings") {
            inclusive = true
        }
    }
}
