package com.speedsense.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.speedsense.app.R
import com.speedsense.app.databinding.ActivityMainBinding
import com.speedsense.app.service.LocationService
import com.speedsense.app.service.MonitoringStateStore
import com.speedsense.app.vibration.VibrationManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var vibrationManager: VibrationManager
    private var pendingStartAfterPermissionFlow = false

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val foregroundPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true

            if (granted) {
                requestBackgroundLocationIfNeeded()
            } else {
                pendingStartAfterPermissionFlow = false
                showToast(getString(R.string.permission_location_required))
            }
        }

    private val backgroundPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                requestNotificationPermissionIfNeeded()
            } else {
                pendingStartAfterPermissionFlow = false
                showToast(getString(R.string.permission_background_required))
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            startMonitoringIfReady()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vibrationManager = VibrationManager(applicationContext)

        binding.startButton.setOnClickListener {
            pendingStartAfterPermissionFlow = true
            beginPermissionFlow()
        }

        binding.stopButton.setOnClickListener {
            pendingStartAfterPermissionFlow = false
            LocationService.stop(this)
        }

        setupVibrationTests()
        observeMonitoringState()
    }

    override fun onResume() {
        super.onResume()
        if (pendingStartAfterPermissionFlow && hasForegroundLocationPermission() && hasBackgroundLocationPermission()) {
            requestNotificationPermissionIfNeeded()
        }
    }

    private fun setupVibrationTests() {
        binding.testVib20.setOnClickListener { vibrationManager.vibrateForSpeedLimit(20) }
        binding.testVib30.setOnClickListener { vibrationManager.vibrateForSpeedLimit(30) }
        binding.testVib40.setOnClickListener { vibrationManager.vibrateForSpeedLimit(40) }
        binding.testVib50.setOnClickListener { vibrationManager.vibrateForSpeedLimit(50) }
        binding.testVib60.setOnClickListener { vibrationManager.vibrateForSpeedLimit(60) }
        binding.testVib70.setOnClickListener { vibrationManager.vibrateForSpeedLimit(70) }
    }

    private fun observeMonitoringState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                MonitoringStateStore.state.collect { state ->
                    binding.speedLimitText.text = state.currentSpeedLimit?.let {
                        getString(R.string.speed_limit_format, it)
                    } ?: getString(R.string.no_speed_limit)

                    binding.roadNameText.text = state.currentRoadName?.let {
                        getString(R.string.road_name_format, it)
                    } ?: getString(R.string.no_road_detected)

                    binding.currentSpeedText.text = state.speedMph?.let {
                        getString(R.string.current_speed_format, it)
                    } ?: getString(R.string.no_speed)

                    binding.coordinatesText.text = if (state.latitude != null && state.longitude != null) {
                        getString(R.string.coordinates_format, state.latitude, state.longitude)
                    } else {
                        getString(R.string.no_coordinates)
                    }

                    binding.timeText.text = state.lastUpdateTimeMs?.let {
                        getString(R.string.time_format, timeFormat.format(Date(it)))
                    } ?: getString(R.string.no_time)

                    binding.statusText.text = when {
                        state.errorMessage != null -> state.errorMessage
                        state.isMonitoring && state.currentSpeedLimit == null -> getString(R.string.status_waiting)
                        state.isMonitoring -> getString(R.string.status_active)
                        else -> getString(R.string.status_idle)
                    }

                    binding.startButton.isEnabled = !state.isMonitoring
                    binding.stopButton.isEnabled = state.isMonitoring
                }
            }
        }
    }

    private fun beginPermissionFlow() {
        when {
            !hasForegroundLocationPermission() -> requestForegroundLocationPermissions()
            !hasBackgroundLocationPermission() -> requestBackgroundLocationIfNeeded()
            else -> requestNotificationPermissionIfNeeded()
        }
    }

    private fun requestForegroundLocationPermissions() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            showDialog(
                title = getString(R.string.app_name),
                message = getString(R.string.foreground_location_rationale),
                onPositive = {
                    foregroundPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    )
                },
            )
        } else {
            foregroundPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    private fun requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || hasBackgroundLocationPermission()) {
            requestNotificationPermissionIfNeeded()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            showDialog(
                title = getString(R.string.app_name),
                message = getString(R.string.background_location_rationale) + "\n\n" +
                    getString(R.string.settings_instruction),
                positiveButtonText = getString(R.string.open_settings),
                onPositive = { openAppSettings() },
            )
            return
        }

        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            showDialog(
                title = getString(R.string.app_name),
                message = getString(R.string.background_location_rationale),
                onPositive = {
                    backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                },
            )
        } else {
            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasNotificationPermission()) {
            startMonitoringIfReady()
            return
        }

        if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            showDialog(
                title = getString(R.string.app_name),
                message = getString(R.string.notification_permission_rationale),
                onPositive = {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
            )
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startMonitoringIfReady() {
        if (!hasForegroundLocationPermission() || !hasBackgroundLocationPermission()) {
            pendingStartAfterPermissionFlow = false
            showToast(getString(R.string.permission_background_required))
            return
        }

        pendingStartAfterPermissionFlow = false
        LocationService.start(this)
    }

    private fun hasForegroundLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            )
        )
    }

    private fun showDialog(
        title: String,
        message: String,
        positiveButtonText: String = getString(android.R.string.ok),
        onPositive: () -> Unit,
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButtonText) { _, _ -> onPositive() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
