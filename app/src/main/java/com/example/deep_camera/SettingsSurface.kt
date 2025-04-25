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
import androidx.compose.runtime.mutableStateOf
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
fun SettingsSurface(
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
                    val textState = remember {mutableStateOf(item.focusAt.toString())}
                    OutlinedTextField(
                        modifier = Modifier.padding(2.dp),
                        value = textState.value,
                        onValueChange = { newValue ->
                            textState.value = newValue
                            item.focusAt = newValue.toFloatOrNull() ?: 0.0F
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
}


@Preview(showBackground = true)
@Composable
fun SettingSurfacePreview() {
    SettingsSurface()
}

class FocusItem(var focusAt: Float, var selected: Boolean)
class ZoomRatioRange(var min: Float, var max: Float)

private val defaultFocusArray = arrayOf<FocusItem>(
    FocusItem(1.0F, true),
    FocusItem(2.0f, true),
    FocusItem(3.0f, true),
    FocusItem(4.0f, true),
    FocusItem(5.0f, true),
    FocusItem(6.0f, true),
    FocusItem(7.0f, true),
    FocusItem(8.0f, true)
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

private fun getLocalZoomRatioRange(context: android.content.Context) {

}

