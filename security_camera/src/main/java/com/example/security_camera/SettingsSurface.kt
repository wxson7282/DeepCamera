package com.example.security_camera

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSurface(
    navController: NavController? = null, sharedPreferences: SharedPreferences? = null
) {
    // 输入对话框是否打开
    val openDialog4VideoClipLength = remember { mutableStateOf(false) }
    val openDialog4StorageSpace = remember { mutableStateOf(false) }
    // 视频片段长度，以分钟为单位
    val mutableStateOfVideoClipLength =
        remember { mutableIntStateOf(sharedPreferences?.getInt("video_clip_length", 3) ?: 3) }
    // 存储空间，以GB为单位
    val mutableStateOfStorageSpace =
        remember { mutableIntStateOf(sharedPreferences?.getInt("storage_space", 32) ?: 32) }
    // 视频质量格式
    val videoQualityOptions = listOf("SD", "HD", "FHD")
    val mutableVideoQuality =
        remember { mutableStateOf(sharedPreferences?.getString("video_quality", videoQualityOptions[0]) ?: videoQualityOptions[0]) }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Security Camera Settings") }, navigationIcon = {
            IconButton(
                modifier = Modifier.background(MaterialTheme.colorScheme.primary),
                onClick = {
                    navigateToMain(navController)
                }) {
                Icon(
                    imageVector = Icons.Outlined.Home, contentDescription = "Home"
                )
            }
        })
    },
        bottomBar = {
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
                            var value = mutableStateOfVideoClipLength.intValue
                            if (value in 1..10){
                                putInt("video_clip_length", value)
                            }
                            else {
                                openDialog4VideoClipLength.value = true
                                return@edit
                            }
                            value = mutableStateOfStorageSpace.intValue
                            if (value in 1..128){
                                putInt("storage_space", value)
                            }
                            else {
                                openDialog4StorageSpace.value = true
                                return@edit
                            }
                            putString("video_quality", mutableVideoQuality.value)
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
                val videoClipLength = mutableStateOfVideoClipLength.intValue.toString()
                OutlinedTextField(
                    modifier = Modifier.padding(2.dp),
                    label = { Text("视频片段长度（1-10分钟）") },
                    value = videoClipLength,
                    onValueChange = { newValue ->
                        mutableStateOfVideoClipLength.intValue = newValue.toIntOrNull()?: 0
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val storageSpace = mutableStateOfStorageSpace.intValue.toString()
                OutlinedTextField(
                    modifier = Modifier.padding(2.dp),
                    label = { Text("存储空间（1-128GB）") },
                    value = storageSpace,
                    onValueChange = { newValue ->
                        mutableStateOfStorageSpace.intValue = newValue.toIntOrNull()?: 0
                    }
                )
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
                                selected = (mutableVideoQuality.value == text),
                                onClick = { mutableVideoQuality.value = text },
                                role = Role.RadioButton
                            )
                    ) {
                        RadioButton(
                            selected = (mutableVideoQuality.value == text),
                            onClick = null
                        )
                        Text(text)
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
            }
        )
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
            }
        )
    }
}

private fun navigateToMain(navController: NavController?) {
    navController?.navigate("main") {
        popUpTo("settings") {
            inclusive = true
        }
    }
}
