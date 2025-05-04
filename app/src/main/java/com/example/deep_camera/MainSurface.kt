package com.example.deep_camera

import android.content.Context
import android.content.SharedPreferences
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OnImageSavedCallback
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSurface(
    navController: NavController? = null,
    sharedPreferences: SharedPreferences? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusList = Util.loadFocusArray(sharedPreferences) ?: defaultFocusArray
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
                    onClick = { Util.takePictures(context, lifecycleOwner, focusList) }) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.circle),
                        contentDescription = "Shutter"
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    ) { paddingValues ->
        Modifier.padding(paddingValues).Camera()
    }
}

@Preview(showBackground = true)
@Composable
fun MainSurfacePreview() {
    MainSurface()
}
