package com.touchlock.calls.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.touchlock.calls.R
import com.touchlock.calls.overlay.TouchLockOverlayManager
import com.touchlock.calls.preferences.AppPreferences
import com.touchlock.calls.util.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class CallDetectionService : Service() {

    companion object {
        const val CHANNEL_ID = "touch_lock_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.touchlock.calls.action.START"
        const val ACTION_STOP = "com.touchlock.calls.action.STOP"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var overlayManager: TouchLockOverlayManager
    private lateinit var preferences: AppPreferences

    private var isFeatureEnabled = true
    private var lockDelaySeconds = 2
    private var isUnlockedByTapForCurrentCall = false

    private var pendingLockRunnable: Runnable? = null

    // For API 31+ (Android 12+)
    private var telephonyCallback: Any? = null

    // For API 26-30 (Android 8.0 - 11)
    @Suppress("DEPRECATION")
    private var phoneStateListener: android.telephony.PhoneStateListener? = null

    override fun onCreate() {
        super.onCreate()
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        preferences = AppPreferences(this)

        overlayManager = TouchLockOverlayManager(this) {
            // Callback when user double-taps overlay to unlock
            isUnlockedByTapForCurrentCall = true
        }

        createNotificationChannel()
        startForegroundServiceNotification()

        observePreferences()
        registerCallStateListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34+
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification to maintain background call detection for Touch Lock."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun observePreferences() {
        serviceScope.launch {
            combine(
                preferences.isEnabledFlow,
                preferences.lockDelaySecondsFlow
            ) { enabled, delay ->
                Pair(enabled, delay)
            }.collect { (enabled, delay) ->
                isFeatureEnabled = enabled
                lockDelaySeconds = delay

                if (!isFeatureEnabled) {
                    cancelPendingLock()
                    overlayManager.hideOverlay()
                }
            }
        }
    }

    private fun registerCallStateListener() {
        if (!PermissionUtils.hasPhoneStatePermission(this)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31+
            val callback = @RequiresApi(Build.VERSION_CODES.S) object : TelephonyCallback(),
                TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleCallStateChanged(state)
                }
            }
            telephonyCallback = callback
            try {
                telephonyManager.registerTelephonyCallback(mainExecutor, callback)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            @Suppress("DEPRECATION")
            val listener = object : android.telephony.PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, incomingNumber: String?) {
                    handleCallStateChanged(state)
                }
            }
            phoneStateListener = listener
            try {
                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun unregisterCallStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (telephonyCallback as? TelephonyCallback)?.let {
                try {
                    telephonyManager.unregisterTelephonyCallback(it)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            phoneStateListener?.let {
                try {
                    @Suppress("DEPRECATION")
                    telephonyManager.listen(it, android.telephony.PhoneStateListener.LISTEN_NONE)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun handleCallStateChanged(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Call is dialing, incoming answered, or ongoing active
                if (!isFeatureEnabled) return
                if (isUnlockedByTapForCurrentCall) return // User already unlocked during this call session

                cancelPendingLock()

                val runnable = Runnable {
                    if (isFeatureEnabled && !isUnlockedByTapForCurrentCall && PermissionUtils.hasOverlayPermission(this)) {
                        overlayManager.showOverlay()
                    }
                }
                pendingLockRunnable = runnable
                handler.postDelayed(runnable, lockDelaySeconds * 1000L)
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                // Call ended or missed
                cancelPendingLock()
                overlayManager.hideOverlay()
                isUnlockedByTapForCurrentCall = false // Reset unlock state for future calls
            }

            TelephonyManager.CALL_STATE_RINGING -> {
                // Incoming call is ringing, wait until user answers (OFFHOOK)
                // Reset session state
                isUnlockedByTapForCurrentCall = false
            }
        }
    }

    private fun cancelPendingLock() {
        pendingLockRunnable?.let {
            handler.removeCallbacks(it)
        }
        pendingLockRunnable = null
    }

    override fun onDestroy() {
        cancelPendingLock()
        overlayManager.hideOverlay()
        unregisterCallStateListener()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
