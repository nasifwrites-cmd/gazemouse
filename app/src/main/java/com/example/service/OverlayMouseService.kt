package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity

/**
 * System floating overlay service allowing GazeMouse to float on top of any other app or home screen.
 */
class OverlayMouseService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var cursorView: View? = null

    companion object {
        const val CHANNEL_ID = "gazemouse_overlay_channel"
        const val NOTIFICATION_ID = 1001
        var isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startForegroundServiceNotification()
        setupFloatingOverlay()
    }

    private fun startForegroundServiceNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GazeMouse Overlay Active",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Floating cursor and fingertip tracking active over other apps"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GazeMouse Overlay Active")
            .setContentText("Tracking fingertip pointer & pinch click over other apps.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun setupFloatingOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Floating HUD badge
        val hudParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 120
        }

        // Create programmatic floating HUD view
        val hudLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(24, 16, 24, 16)
            setBackgroundColor(android.graphics.Color.parseColor("#CC0B132B"))
            elevation = 16f
        }

        val title = TextView(this).apply {
            text = "👁️ GazeMouse Overlay"
            setTextColor(android.graphics.Color.parseColor("#38BDF8"))
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        hudLayout.addView(title)

        val spacer = View(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(24, 1)
        }
        hudLayout.addView(spacer)

        val closeBtn = TextView(this).apply {
            text = "✕ Close"
            setTextColor(android.graphics.Color.parseColor("#F43F5E"))
            textSize = 12f
            setPadding(12, 4, 12, 4)
            setOnClickListener {
                stopSelf()
            }
        }
        hudLayout.addView(closeBtn)

        overlayView = hudLayout

        try {
            windowManager?.addView(overlayView, hudParams)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
        }
        overlayView = null
    }
}
