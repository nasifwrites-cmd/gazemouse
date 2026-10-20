package com.example.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioSnapDetector
import com.example.data.local.AppDatabase
import com.example.data.local.CalibrationProfileEntity
import com.example.data.local.ClickLogEntity
import com.example.data.models.CalibrationMatrix
import com.example.data.models.CalibrationPoint
import com.example.vision.EyeGazeTracker
import com.example.vision.GazeCalibrationEngine
import com.example.vision.HandFingertipTracker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot
import kotlin.random.Random

enum class AppScreen {
    PLAYGROUND,
    CALIBRATION,
    SOUND_TUNING,
    LOCAL_STORAGE
}

enum class PlaygroundMode {
    SCROLL_FEED,
    TARGET_GAME,
    VIRTUAL_KEYPAD,
    EYE_CANVAS
}

enum class TrackingMode {
    FINGERTIP_TRACKING,
    EYE_GAZE_TRACKING
}

data class TargetBubble(
    val id: Int,
    val xRatio: Float,
    val yRatio: Float,
    val radiusPx: Float = 75f,
    val colorIndex: Int = 0,
    val points: Int = 100
)

data class CanvasDot(
    val x: Float,
    val y: Float,
    val colorHex: Long,
    val radius: Float = 14f
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val calibrationDao = db.calibrationDao()
    private val clickLogDao = db.clickLogDao()

    val gazeTracker = EyeGazeTracker()
    val fingerTracker = HandFingertipTracker()
    val audioDetector = AudioSnapDetector(application)
    val calibrationEngine = GazeCalibrationEngine(gazeTracker)

    // Navigation & Mode State
    private val _currentScreen = MutableStateFlow(AppScreen.PLAYGROUND)
    val currentScreen = _currentScreen.asStateFlow()

    private val _playgroundMode = MutableStateFlow(PlaygroundMode.SCROLL_FEED)
    val playgroundMode = _playgroundMode.asStateFlow()

    // Tracking mode: Fingertip mouse is default
    val trackingMode = MutableStateFlow(TrackingMode.FINGERTIP_TRACKING)

    // Camera settings: No face background
    val showCameraPip = MutableStateFlow(false)

    // Permissions state
    val hasCameraPermission = MutableStateFlow(false)
    val hasMicPermission = MutableStateFlow(false)
    val hasOverlayPermission = MutableStateFlow(false)

    // Scroll stream from fingertip tracker
    val scrollEvents = fingerTracker.scrollEvents

    // Active Cursor Position depending on tracking mode
    val cursorPosition = combine(
        trackingMode,
        fingerTracker.cursorPosition,
        gazeTracker.smoothedCursor
    ) { mode, fingerPos, gazePos ->
        when (mode) {
            TrackingMode.FINGERTIP_TRACKING -> fingerPos
            TrackingMode.EYE_GAZE_TRACKING -> gazePos
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(540f, 1200f))

    // Live streams from tracker & audio
    val fingerState = fingerTracker.fingerState
    val rawGaze = gazeTracker.rawGaze
    val eyeFeatures = gazeTracker.eyeFeatures
    val currentDecibel = audioDetector.currentDb
    val isListening = audioDetector.isListening
    val snapThresholdDb = audioDetector.snapThresholdDb

    // Calibration data from DB
    val savedProfiles = calibrationDao.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProfile = calibrationDao.getActiveProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val recentClickLogs = clickLogDao.getRecentLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Visual ripple effect on click
    private val _clickRipple = MutableSharedFlow<Pair<Float, Float>>(extraBufferCapacity = 5)
    val clickRipple = _clickRipple.asSharedFlow()

    // Game state
    private val _gameScore = MutableStateFlow(0)
    val gameScore = _gameScore.asStateFlow()

    private val _gameStreak = MutableStateFlow(0)
    val gameStreak = _gameStreak.asStateFlow()

    private val _activeBubble = MutableStateFlow<TargetBubble?>(null)
    val activeBubble = _activeBubble.asStateFlow()

    // Keypad state
    private val _keypadDisplay = MutableStateFlow("")
    val keypadDisplay = _keypadDisplay.asStateFlow()

    // Canvas state
    private val _canvasDots = MutableStateFlow<List<CanvasDot>>(emptyList())
    val canvasDots = _canvasDots.asStateFlow()
    private val brushColors = listOf(0xFF38BDF8, 0xFFF43F5E, 0xFF10B981, 0xFFFBBF24, 0xFFA855F7)
    private var currentBrushColorIndex = 0

    // Toast/Message state
    private val _systemNotice = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val systemNotice = _systemNotice.asSharedFlow()

    init {
        // Load active profile from database on startup
        viewModelScope.launch {
            val active = calibrationDao.getActiveProfileOnce()
            if (active != null) {
                val matrix = GazeCalibrationEngine.jsonToMatrix(active.matrixJson)
                gazeTracker.setCalibrationMatrix(matrix)
            }
        }

        // Listen for Two-Finger Pinch Clicks
        viewModelScope.launch {
            fingerTracker.pinchClickEvents.collect { (px, py) ->
                handlePinchClick(px, py)
            }
        }

        // Listen for acoustic snap events
        viewModelScope.launch {
            audioDetector.snapEvents.collect { peakDb ->
                handleSnapClick(peakDb)
            }
        }

        // Spawn first target bubble
        spawnNewTargetBubble()
    }

    fun setScreen(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun setPlaygroundMode(mode: PlaygroundMode) {
        _playgroundMode.value = mode
    }

    fun setTrackingMode(mode: TrackingMode) {
        trackingMode.value = mode
        val modeName = if (mode == TrackingMode.FINGERTIP_TRACKING) "Fingertip Tracking" else "Eye Gaze Tracking"
        _systemNotice.tryEmit("Mode: $modeName")
    }

    fun updateScreenDimensions(width: Float, height: Float) {
        gazeTracker.updateScreenDimensions(width, height)
        fingerTracker.updateScreenDimensions(width, height)
    }

    fun startListeningAudio() {
        if (hasMicPermission.value) {
            audioDetector.startListening()
        }
    }

    fun stopListeningAudio() {
        audioDetector.stopListening()
    }

    fun setSnapThreshold(db: Float) {
        audioDetector.snapThresholdDb.value = db
    }

    fun setScrollSensitivity(multiplier: Float) {
        fingerTracker.scrollSensitivity = multiplier
    }

    fun setSmoothing(alpha: Float) {
        gazeTracker.smoothingAlpha = alpha
        fingerTracker.smoothingAlpha = alpha
    }

    fun setSensitivity(x: Float, y: Float) {
        gazeTracker.sensitivityX = x
        gazeTracker.sensitivityY = y
        fingerTracker.sensitivity = x
    }

    /**
     * Called whenever user brings two fingers together (Pinch gesture).
     */
    fun handlePinchClick(cursorX: Float, cursorY: Float) {
        _clickRipple.tryEmit(Pair(cursorX, cursorY))
        triggerHaptic()

        viewModelScope.launch {
            clickLogDao.insertLog(
                ClickLogEntity(
                    cursorX = cursorX,
                    cursorY = cursorY,
                    decibels = 0f,
                    eventType = "PINCH_CLICK"
                )
            )
        }

        dispatchClickAction(cursorX, cursorY, "✌️ TWO-FINGER PINCH CLICK!")
    }

    /**
     * Called whenever an acoustic snap or manual click is triggered.
     */
    fun handleSnapClick(peakDb: Float = 80f) {
        val (cursorX, cursorY) = cursorPosition.value
        _clickRipple.tryEmit(Pair(cursorX, cursorY))
        triggerHaptic()

        viewModelScope.launch {
            clickLogDao.insertLog(
                ClickLogEntity(
                    cursorX = cursorX,
                    cursorY = cursorY,
                    decibels = peakDb,
                    eventType = "SNAP_CLICK"
                )
            )
        }

        dispatchClickAction(cursorX, cursorY, "🫰 SNAP CLICK!")
    }

    private fun dispatchClickAction(cursorX: Float, cursorY: Float, label: String) {
        when (_playgroundMode.value) {
            PlaygroundMode.SCROLL_FEED -> {
                // Handled in UI card/button click
            }
            PlaygroundMode.TARGET_GAME -> {
                checkBubbleHit(cursorX, cursorY)
            }
            PlaygroundMode.EYE_CANVAS -> {
                addCanvasDot(cursorX, cursorY)
            }
            PlaygroundMode.VIRTUAL_KEYPAD -> {
                // Handled in UI button bounds check
            }
        }
    }

    private fun checkBubbleHit(cursorX: Float, cursorY: Float) {
        val bubble = _activeBubble.value ?: return
        val bubblePxX = bubble.xRatio * gazeTracker.screenWidthPx
        val bubblePxY = bubble.yRatio * gazeTracker.screenHeightPx
        val dist = hypot(cursorX - bubblePxX, cursorY - bubblePxY)

        if (dist <= bubble.radiusPx + 45f) {
            _gameScore.value += bubble.points + (_gameStreak.value * 25)
            _gameStreak.value += 1
            _systemNotice.tryEmit("🎯 HIT! +${bubble.points} pts (Streak: ${_gameStreak.value})")
            spawnNewTargetBubble()
        } else {
            _gameStreak.value = 0
            _systemNotice.tryEmit("Clicked at (${cursorX.toInt()}, ${cursorY.toInt()})")
        }
    }

    fun spawnNewTargetBubble() {
        val randX = Random.nextFloat() * 0.7f + 0.15f
        val randY = Random.nextFloat() * 0.6f + 0.20f
        val nextId = (_activeBubble.value?.id ?: 0) + 1
        _activeBubble.value = TargetBubble(
            id = nextId,
            xRatio = randX,
            yRatio = randY,
            colorIndex = nextId % 4
        )
    }

    fun onKeypadButtonHit(label: String) {
        triggerHaptic()
        when (label) {
            "C" -> _keypadDisplay.value = ""
            "⌫" -> {
                if (_keypadDisplay.value.isNotEmpty()) {
                    _keypadDisplay.value = _keypadDisplay.value.dropLast(1)
                }
            }
            else -> {
                if (_keypadDisplay.value.length < 12) {
                    _keypadDisplay.value += label
                }
            }
        }
    }

    private fun addCanvasDot(x: Float, y: Float) {
        val currentColor = brushColors[currentBrushColorIndex % brushColors.size]
        _canvasDots.value = _canvasDots.value + CanvasDot(x, y, currentColor)
    }

    fun cycleCanvasColor() {
        currentBrushColorIndex++
    }

    fun clearCanvas() {
        _canvasDots.value = emptyList()
    }

    fun saveCalibrationProfile(name: String, matrix: CalibrationMatrix, points: List<CalibrationPoint>, accuracy: Float) {
        viewModelScope.launch {
            calibrationDao.deactivateAllProfiles()
            val entity = CalibrationProfileEntity(
                name = name.ifBlank { "Profile " + SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date()) },
                gridPointsCount = points.size,
                accuracyScore = accuracy,
                matrixJson = GazeCalibrationEngine.matrixToJson(matrix),
                pointsDataJson = GazeCalibrationEngine.pointsToJson(points),
                isActive = true
            )
            calibrationDao.insertProfile(entity)
            gazeTracker.setCalibrationMatrix(matrix)
            _systemNotice.tryEmit("Calibration profile saved!")
        }
    }

    fun activateProfile(profile: CalibrationProfileEntity) {
        viewModelScope.launch {
            calibrationDao.deactivateAllProfiles()
            calibrationDao.setActiveProfile(profile.id)
            val matrix = GazeCalibrationEngine.jsonToMatrix(profile.matrixJson)
            gazeTracker.setCalibrationMatrix(matrix)
            _systemNotice.tryEmit("Switched to profile '${profile.name}'")
        }
    }

    fun deleteProfile(profile: CalibrationProfileEntity) {
        viewModelScope.launch {
            calibrationDao.deleteProfile(profile)
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            clickLogDao.clearLogs()
        }
    }

    fun exportDataToJson(context: Context): File? {
        return try {
            val root = JSONObject()
            root.put("appName", "GazeMouse")
            root.put("exportTime", System.currentTimeMillis())
            root.put("date", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))

            val profilesArray = org.json.JSONArray()
            savedProfiles.value.forEach { p ->
                val pObj = JSONObject()
                pObj.put("id", p.id)
                pObj.put("name", p.name)
                pObj.put("createdAt", p.createdAt)
                pObj.put("accuracyScore", p.accuracyScore.toDouble())
                pObj.put("matrix", JSONObject(p.matrixJson))
                pObj.put("points", org.json.JSONArray(p.pointsDataJson))
                profilesArray.put(pObj)
            }
            root.put("calibrationProfiles", profilesArray)

            val logsArray = org.json.JSONArray()
            recentClickLogs.value.forEach { l ->
                val lObj = JSONObject()
                lObj.put("id", l.id)
                lObj.put("timestamp", l.timestamp)
                lObj.put("cursorX", l.cursorX.toDouble())
                lObj.put("cursorY", l.cursorY.toDouble())
                lObj.put("decibels", l.decibels.toDouble())
                lObj.put("eventType", l.eventType)
                logsArray.put(lObj)
            }
            root.put("clickLogs", logsArray)

            val fileName = "gazemouse_export_${System.currentTimeMillis()}.json"
            val file = File(context.filesDir, fileName)
            file.writeText(root.toString(2))
            _systemNotice.tryEmit("Data exported to ${file.name}")
            file
        } catch (e: Exception) {
            _systemNotice.tryEmit("Export failed: ${e.localizedMessage}")
            null
        }
    }

    private fun triggerHaptic() {
        try {
            val context = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(45)
            }
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        audioDetector.stopListening()
        calibrationEngine.cancelCalibration()
    }
}
