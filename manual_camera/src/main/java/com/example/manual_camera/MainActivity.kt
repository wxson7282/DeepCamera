package com.example.manual_camera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.manual_camera.ui.theme.ManualCameraTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ManualCameraTheme {
                Content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Content() {
    var stateOfZoomRatio by remember { mutableFloatStateOf(0f) }
    var stateOfFocusDistance by remember { mutableFloatStateOf(0f) }

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
                    Slider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(2.dp)
                            .border(2.dp, MaterialTheme.colorScheme.primary),
                        value = stateOfZoomRatio,
                        onValueChange = { stateOfZoomRatio = it }
                    )
                    Slider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(2.dp)
                            .border(2.dp, MaterialTheme.colorScheme.primary),
                        value = stateOfFocusDistance,
                        onValueChange = { stateOfFocusDistance = it }
                    )
                }
            }
        }
    ) { innerPadding ->
        Modifier.fillMaxSize().padding(innerPadding).CameraPreview(
            zoomRatio = stateOfZoomRatio,
            focusDistance = stateOfFocusDistance
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ContentPreview() {
    ManualCameraTheme {
        Content()
    }
}
