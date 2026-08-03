package com.touchlock.calls

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.touchlock.calls.databinding.ActivityMainBinding
import com.touchlock.calls.preferences.AppPreferences
import com.touchlock.calls.service.CallDetectionService
import com.touchlock.calls.util.PermissionUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferences: AppPreferences

    // Overlay Permission Launcher
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionViewsAndService()
    }

    // Runtime Permission Launcher for Phone State & Notifications
    private val runtimePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        updatePermissionViewsAndService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = AppPreferences(this)

        setupListeners()
        observePreferences()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionViewsAndService()
    }

    private fun setupListeners() {
        // Toggle Switch
        binding.switchEnable.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                preferences.setEnabled(isChecked)
            }
        }

        // Delay Selection Buttons
        binding.toggleGroupDelay.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val delay = when (checkedId) {
                    R.id.btnDelay1 -> 1
                    R.id.btnDelay2 -> 2
                    R.id.btnDelay3 -> 3
                    R.id.btnDelay5 -> 5
                    else -> 2
                }
                lifecycleScope.launch {
                    preferences.setLockDelaySeconds(delay)
                }
            }
        }

        // Grant Permissions Button
        binding.btnGrantPermissions.setOnClickListener {
            requestMissingPermissions()
        }
    }

    private fun observePreferences() {
        lifecycleScope.launch {
            val isEnabled = preferences.isEnabledFlow.first()
            val delay = preferences.lockDelaySecondsFlow.first()

            binding.switchEnable.isChecked = isEnabled

            when (delay) {
                1 -> binding.toggleGroupDelay.check(R.id.btnDelay1)
                2 -> binding.toggleGroupDelay.check(R.id.btnDelay2)
                3 -> binding.toggleGroupDelay.check(R.id.btnDelay3)
                5 -> binding.toggleGroupDelay.check(R.id.btnDelay5)
                else -> binding.toggleGroupDelay.check(R.id.btnDelay2)
            }
        }

        lifecycleScope.launch {
            preferences.isEnabledFlow.collect { enabled ->
                binding.switchEnable.isChecked = enabled
                updatePermissionViewsAndService()
            }
        }
    }

    private fun updatePermissionViewsAndService() {
        val hasOverlay = PermissionUtils.hasOverlayPermission(this)
        val hasPhone = PermissionUtils.hasPhoneStatePermission(this)
        val hasNotif = PermissionUtils.hasNotificationPermission(this)

        // Overlay status text
        binding.tvOverlayPerm.text = if (hasOverlay) {
            getString(R.string.overlay_perm_granted)
        } else {
            getString(R.string.overlay_perm_not_granted)
        }

        // Phone state status text
        binding.tvPhonePerm.text = if (hasPhone) {
            getString(R.string.phone_perm_granted)
        } else {
            getString(R.string.phone_perm_not_granted)
        }

        // Notification status text
        if (PermissionUtils.needsNotificationPermission()) {
            binding.tvNotifPerm.visibility = android.view.View.VISIBLE
            binding.tvNotifPerm.text = if (hasNotif) {
                getString(R.string.notif_perm_granted)
            } else {
                getString(R.string.notif_perm_not_granted)
            }
        } else {
            binding.tvNotifPerm.visibility = android.view.View.GONE
        }

        val allGranted = hasOverlay && hasPhone && hasNotif
        val isEnabled = binding.switchEnable.isChecked

        if (isEnabled && allGranted) {
            binding.tvStatus.text = getString(R.string.status_service_active)
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            startCallDetectionService()
        } else if (!isEnabled) {
            binding.tvStatus.text = getString(R.string.status_service_disabled)
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.white))
            stopCallDetectionService()
        } else {
            binding.tvStatus.text = getString(R.string.status_service_inactive)
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
            stopCallDetectionService()
        }
    }

    private fun requestMissingPermissions() {
        // 1. Overlay permission check
        if (!PermissionUtils.hasOverlayPermission(this)) {
            val intent = PermissionUtils.getOverlayPermissionIntent(this)
            overlayPermissionLauncher.launch(intent)
            Toast.makeText(this, "Please enable 'Display over other apps'", Toast.LENGTH_LONG).show()
            return
        }

        // 2. Runtime permissions check
        val permissionsToRequest = mutableListOf<String>()
        if (!PermissionUtils.hasPhoneStatePermission(this)) {
            permissionsToRequest.add(Manifest.permission.READ_PHONE_STATE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !PermissionUtils.hasNotificationPermission(this)) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            runtimePermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            Toast.makeText(this, "All required permissions are already granted!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCallDetectionService() {
        val intent = Intent(this, CallDetectionService::class.java).apply {
            action = CallDetectionService.ACTION_START
        }
        try {
            ContextCompat.startForegroundService(this, intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopCallDetectionService() {
        val intent = Intent(this, CallDetectionService::class.java).apply {
            action = CallDetectionService.ACTION_STOP
        }
        try {
            startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
