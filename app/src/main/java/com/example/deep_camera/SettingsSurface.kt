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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.navigation.NavController
import com.google.gson.Gson

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingSurface(
    navController: NavController? = null,
    sharedPreferences: SharedPreferences? = null
) {
    val mutableStateFocusList = remember {
        mutableStateListOf<FocusItem>(
            *(if (sharedPreferences != null) {
                loadFocusArray(sharedPreferences) ?: defaultFocusArray
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
                                saveFocusArray(it, mutableStateFocusList.toTypedArray())
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
                    OutlinedTextField(
                        modifier = Modifier.padding(2.dp),
                        value = item.focusAt.toString(),
                        onValueChange = { newValue ->
                            item.focusAt = newValue.toFloatOrNull() ?: 0.0F
                        }
                    )
                    Checkbox(
                        checked = item.selected,
                        onCheckedChange = { newValue ->
                            item.selected = newValue
                        })
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun SettingSurfacePreview() {
    SettingSurface()
}

class FocusItem(var focusAt: Float, var selected: Boolean)

private val defaultFocusArray = arrayOf<FocusItem>(
    FocusItem(0.0F, false),
    FocusItem(0.01f, true),
    FocusItem(0.04f, true),
    FocusItem(0.05f, true),
    FocusItem(0.08f, true),
    FocusItem(0.1f, true),
    FocusItem(0.3f, true),
    FocusItem(0.5f, true),
    FocusItem(0.7f, true),
    FocusItem(1.0f, true)
)

private fun loadFocusArray(sharedPreferences: SharedPreferences): Array<FocusItem>? {
    val json = sharedPreferences.getString("focusArray", null)
    return json?.let { Gson().fromJson(it, Array<FocusItem>::class.java) }
}

private fun saveFocusArray(sharedPreferences: SharedPreferences, focusArray: Array<FocusItem>) {
    val json = Gson().toJson(focusArray)
    sharedPreferences.edit { putString("focusArray", json) }
}

private fun navigateToMain(navController: NavController?) {
    navController?.navigate("main") {
        popUpTo("settings") {
            inclusive = true
        }
    }
}

