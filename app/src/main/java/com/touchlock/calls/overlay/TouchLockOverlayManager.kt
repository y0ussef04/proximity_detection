package com.touchlock.calls.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.touchlock.calls.util.PermissionUtils

class TouchLockOverlayManager(
    private val context: Context,
    private val onUnlockedByDoubleTap: () -> Unit
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    var isShowing: Boolean = false
        private set

    private var lastTapTime: Long = 0L
    private val doubleTapTimeoutMs: Long = 300L

    fun showOverlay() {
        if (isShowing || overlayView != null) return
        if (!PermissionUtils.hasOverlayPermission(context)) return

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        // Create overlay container programmatically to ensure robust touch interception
        val container = object : LinearLayout(context) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    val now = SystemClock.uptimeMillis()
                    val timeDelta = now - lastTapTime

                    if (timeDelta in 1..doubleTapTimeoutMs) {
                        // Double tap verified -> unlock
                        lastTapTime = 0L
                        hideOverlay()
                        onUnlockedByDoubleTap()
                    } else {
                        lastTapTime = now
                    }
                }
                // CRITICAL REQUIREMENT: Return true for ALL touch events (DOWN, MOVE, UP)
                // to consume touches completely and prevent any touch from reaching underlying call UI.
                return true
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(160, 0, 0, 0)) // ~63% translucent black
            isClickable = true
            isFocusable = false
        }

        // Lock Icon + Title
        val tvTitle = TextView(context).apply {
            text = "🔒 Touch Locked"
            setTextColor(Color.WHITE)
            textSize = 28f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        // Subtitle instructions
        val tvSubtitle = TextView(context).apply {
            text = "Double tap anywhere to unlock"
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 0)
        }

        container.addView(tvTitle)
        container.addView(tvSubtitle)

        try {
            windowManager.addView(container, layoutParams)
            overlayView = container
            isShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
            isShowing = false
            overlayView = null
        }
    }

    fun hideOverlay() {
        val view = overlayView ?: return
        if (!isShowing) return

        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            overlayView = null
            isShowing = false
            lastTapTime = 0L
        }
    }
}
