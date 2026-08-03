package com.touchlock.calls.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.touchlock.calls.preferences.AppPreferences
import com.touchlock.calls.service.CallDetectionService
import com.touchlock.calls.util.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val preferences = AppPreferences(context)
            CoroutineScope(Dispatchers.IO).launch {
                val isEnabled = preferences.isEnabledFlow.first()
                if (isEnabled && PermissionUtils.areAllRequiredPermissionsGranted(context)) {
                    val serviceIntent = Intent(context, CallDetectionService::class.java)
                    try {
                        ContextCompat.startForegroundService(context, serviceIntent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}
