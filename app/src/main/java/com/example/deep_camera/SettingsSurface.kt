package com.example.deep_camera

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSurface(
    navController: NavController? = null,
    sharedPreferences: SharedPreferences? = null
) {
    val openDialog = remember { mutableStateOf(false) }
    val mutableStateFocusList = remember {
        mutableStateListOf<FocusItem>(
            *(if (sharedPreferences != null) {
                Util.loadFocusArray(sharedPreferences) ?: defaultFocusArray
            } else {
                defaultFocusArray
            })
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Deep Camera Settings") },
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.background(MaterialTheme.colorScheme.primary),
                        onClick = {
                            navigateToMain(navController)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Home,
                            contentDescription = "Home"
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
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary),
                        onClick = {
                            sharedPreferences?.let {
                                Util.saveFocusArray(it, mutableStateFocusList.toTypedArray())
                            }
                            navigateToMain(navController)
                        }) {
                        Icon(
                            imageVector = Icons.Filled.Done,
                            contentDescription = "Done"
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary),
                        onClick = { navigateToMain(navController) }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close"
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }

            }
        }
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            mutableStateFocusList.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(2.dp)
                        .border(1.dp, MaterialTheme.colorScheme.primary),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val textState = remember {mutableStateOf(item.focusAt.toString())}
                    OutlinedTextField(
                        modifier = Modifier.padding(2.dp),
                        value = textState.value,
                        onValueChange = { newValue ->
                            if (!checkFocusDistance(newValue.toFloatOrNull()?: 0.0F)) {
                                // 弹出对话框提示用户输入不合法
                                openDialog.value = true
                                // 不合法的输入，不更新状态
                                return@OutlinedTextField
                            } else {
                                textState.value = newValue
                                item.focusAt = newValue.toFloatOrNull() ?: 0.0F
                            }

                        }
                    )
                    val checkedState = remember {mutableStateOf(item.selected)}
                    Checkbox(
                        checked = checkedState.value,
                        onCheckedChange = { newValue ->
                            checkedState.value = newValue
                            item.selected = newValue
                        })
                }
            }
        }
    }
    if (openDialog.value) {
        AlertDialog(
            onDismissRequest = { openDialog.value = false },
            title = { Text("Error") },
            text = { Text("Please input a number between 0 and 1") },
            confirmButton = {
                IconButton(onClick = { openDialog.value = false }) {
                    Icon(Icons.Filled.Done, contentDescription = "OK")
                }
            }
        )
    }
}

val defaultFocusArray = arrayOf<FocusItem>(
    FocusItem(1.0F, true),
    FocusItem(0.8f, true),
    FocusItem(0.6f, true),
    FocusItem(0.4f, true),
    FocusItem(0.2f, true),
    FocusItem(0.1f, true),
    FocusItem(0.0f, true)
)

private fun navigateToMain(navController: NavController?) {
    navController?.navigate("main") {
        popUpTo("settings") {
            inclusive = true
        }
    }
}

//焦点距离合规性检查
private fun checkFocusDistance(focusDistance: Float): Boolean {
//    return focusDistance >= 0.0f && focusDistance <= 1.0f
    return focusDistance >= 0.0f && focusDistance <= 15.0f
}
