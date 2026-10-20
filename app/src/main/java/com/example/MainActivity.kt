package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.screens.CalibrationScreen
import com.example.ui.screens.LocalStorageScreen
import com.example.ui.screens.PlaygroundScreen
import com.example.ui.screens.SoundTuningScreen
import com.example.ui.theme.GazeMouseTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GazeMouseTheme {
                MainContent(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.hasMicPermission.value = true
            viewModel.startListeningAudio()
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            viewModel.hasCameraPermission.value = true
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopListeningAudio()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    val currentScreen by viewModel.currentScreen.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showHelpDialog by remember { mutableStateOf(false) }

    // Runtime Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false

        viewModel.hasCameraPermission.value = cameraGranted
        viewModel.hasMicPermission.value = audioGranted

        if (audioGranted) {
            viewModel.startListeningAudio()
        }
    }

    LaunchedEffect(Unit) {
        val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        viewModel.hasCameraPermission.value = hasCam
        viewModel.hasMicPermission.value = hasMic

        if (!hasCam || !hasMic) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        } else {
            viewModel.startListeningAudio()
        }
    }

    // System notices flow
    LaunchedEffect(Unit) {
        viewModel.systemNotice.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Predictive back handling: Return to Playground if in a subscreen
    BackHandler(enabled = currentScreen != AppScreen.PLAYGROUND) {
        viewModel.setScreen(AppScreen.PLAYGROUND)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "GazeMouse",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                actions = {
                    IconButton(
                        onClick = { showHelpDialog = true },
                        modifier = Modifier.testTag("btn_help_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Help,
                            contentDescription = "Help Guide"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentScreen == AppScreen.PLAYGROUND,
                    onClick = { viewModel.setScreen(AppScreen.PLAYGROUND) },
                    icon = { Icon(Icons.Default.AdsClick, contentDescription = "Mouse Playground") },
                    label = { Text("Mouse", fontSize = 11.sp) },
                    modifier = Modifier.testTag("nav_mouse")
                )
                NavigationBarItem(
                    selected = currentScreen == AppScreen.CALIBRATION,
                    onClick = { viewModel.setScreen(AppScreen.CALIBRATION) },
                    icon = { Icon(Icons.Default.GpsFixed, contentDescription = "Calibration") },
                    label = { Text("Calibrate", fontSize = 11.sp) },
                    modifier = Modifier.testTag("nav_calibrate")
                )
                NavigationBarItem(
                    selected = currentScreen == AppScreen.SOUND_TUNING,
                    onClick = { viewModel.setScreen(AppScreen.SOUND_TUNING) },
                    icon = { Icon(Icons.Default.Tune, contentDescription = "Tuning") },
                    label = { Text("Sound", fontSize = 11.sp) },
                    modifier = Modifier.testTag("nav_sound")
                )
                NavigationBarItem(
                    selected = currentScreen == AppScreen.LOCAL_STORAGE,
                    onClick = { viewModel.setScreen(AppScreen.LOCAL_STORAGE) },
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Local Data") },
                    label = { Text("Storage", fontSize = 11.sp) },
                    modifier = Modifier.testTag("nav_storage")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                AppScreen.PLAYGROUND -> PlaygroundScreen(viewModel = viewModel)
                AppScreen.CALIBRATION -> CalibrationScreen(viewModel = viewModel)
                AppScreen.SOUND_TUNING -> SoundTuningScreen(viewModel = viewModel)
                AppScreen.LOCAL_STORAGE -> LocalStorageScreen(viewModel = viewModel)
            }
        }
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("How GazeMouse Works") },
            text = {
                Text(
                    text = "1. 👆 Fingertip as Mouse Pointer:\n" +
                            "Point your fingertip towards the camera — the mouse cursor tracks where your finger moves!\n\n" +
                            "2. ↕️ Two-Finger Air Scroll:\n" +
                            "Raise 2 fingers up and move them vertically to scroll feeds, news, and pages up and down smoothly!\n\n" +
                            "3. ✌️ Two Fingers Together = Click:\n" +
                            "Pinch your two fingers together to trigger a mouse click at the cursor!\n\n" +
                            "4. 🫰 Sound Snap Click:\n" +
                            "Sharp finger snaps or claps also fire instant clicks.\n\n" +
                            "5. 📱 System Floating Overlay:\n" +
                            "Enable 'Draw Over Other Apps' in Sound settings to use your fingertip mouse over home screen and other apps!",
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Got It")
                }
            }
        )
    }
}
