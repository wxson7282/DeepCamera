package com.example.deep_camera

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSurface(
    navController: NavController? = null,
    sharedPreferences: SharedPreferences? = null,
    shutterSound: ShutterSound? = null
) {
//    val context = LocalContext.current
//    val lifecycleOwner = LocalLifecycleOwner.current
//    val focusList = Util.loadFocusArray(sharedPreferences) ?: defaultFocusArray
//    var stateOfLastImageSaved by remember { mutableStateOf(false) }
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
//        bottomBar = {
//            BottomAppBar {
//                Spacer(modifier = Modifier.weight(1f))
//                IconButton(
//                    modifier = Modifier
//                        .background(MaterialTheme.colorScheme.primary),
//                    onClick = {
//                        Util.takeAllPictures(
//                            context = context,
//                            lifecycleOwner = lifecycleOwner,
//                            focusArray = focusList,
//                            shutterSound = shutterSound,
//                            // 取得最后一张照片是否保存的状态
//                            setStateOfLastImageSaved = { newValue ->
//                                stateOfLastImageSaved = newValue
//                                Log.i("MainSurface", "stateOfLastImageSaved: $newValue")
//                            }
//                        )
//                    }) {
//                    Icon(
//                        imageVector = ImageVector.vectorResource(R.drawable.circle),
//                        contentDescription = "Shutter"
//                    )
//                }
//                Spacer(modifier = Modifier.weight(1f))
//            }
//        }
    ) { paddingValues ->
//        if (stateOfLastImageSaved) {
//            Text(
//                text = "Last Image Saved",
//                modifier = Modifier.padding(paddingValues)
//            )
//            stateOfLastImageSaved = false
//            Log.i("MainSurface", "Camera Preview released")
//        } else {
//            CameraPreview()
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                CameraScreen(
                    sharedPreferences = sharedPreferences,
                    shutterSound = shutterSound
                )
            }

//            Log.i("MainSurface", "Camera Preview updated")
//        }
    }
}
