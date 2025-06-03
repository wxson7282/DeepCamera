package com.example.deep_camera

import android.util.Log
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSurface(
    navController: NavController? = null,
    sharedPreferences: SharedPreferences? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusList = Util.loadFocusArray(sharedPreferences) ?: defaultFocusArray
    var stateOfLastImageSaved by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Deep Camera Main") },
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.background(MaterialTheme.colorScheme.primary),
                        onClick = {
                            navController?.navigate("settings") {
                                popUpTo("main") {
                                    inclusive = true
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings"
                        )
                    }
                })
        },
        bottomBar = {
            BottomAppBar {
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary),
                    onClick = {
                        Util.takeAllPictures(
                            context = context,
                            lifecycleOwner = lifecycleOwner,
                            focusArray = focusList,
                            // 取得最后一张照片是否保存的状态
                            setStateOfLastImageSaved = { newValue ->
                                stateOfLastImageSaved = newValue
                                Log.i("MainSurface", "stateOfLastImageSaved: $newValue")
                            }
                        )
                    }) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.circle),
                        contentDescription = "Shutter"
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    ) { paddingValues ->
        if (stateOfLastImageSaved) {
            Text(
                text = "Last Image Saved",
                modifier = Modifier.padding(paddingValues)
            )
            stateOfLastImageSaved = false
            Log.i("MainSurface", "Camera Preview released")
        } else {
            Modifier.padding(paddingValues).Camera()
            Log.i("MainSurface", "Camera Preview updated")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainSurfacePreview() {
    MainSurface()
}
