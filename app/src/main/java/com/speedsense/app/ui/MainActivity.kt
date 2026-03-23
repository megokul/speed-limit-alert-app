package com.speedsense.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.speedsense.app.R
import com.speedsense.app.data.Road
import com.speedsense.app.data.RoadNameResolver
import com.speedsense.app.data.RoadRepository
import com.speedsense.app.databinding.ActivityMainBinding
import com.speedsense.app.data.SpeedLimitOverrides
import com.speedsense.app.data.UserRoadStorage
import com.speedsense.app.data.GeoPoint
import com.speedsense.app.service.LocationService
import com.speedsense.app.service.MonitoringStateStore
import com.speedsense.app.service.RecordedRoadDraft
import com.speedsense.app.service.RoadRecordingStore
import com.speedsense.app.utils.GeoUtils
import com.speedsense.app.vibration.VibrationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var vibrationManager: VibrationManager
    private lateinit var roadNameResolver: RoadNameResolver
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
        roadNameResolver = RoadNameResolver(applicationContext)

        binding.startButton.setOnClickListener {
            pendingStartAfterPermissionFlow = true
            beginPermissionFlow()
        }

        binding.stopButton.setOnClickListener {
            pendingStartAfterPermissionFlow = false
            LocationService.stop(this)
        }

        binding.viewRoadsButton.setOnClickListener {
            startActivity(Intent(this, RoadListActivity::class.java))
        }

        binding.correctSpeedButton.setOnClickListener {
            showCorrectSpeedLimitDialog()
        }

        setupVibrationTests()
        setupRecordingActions()
        observeMonitoringState()
        observeRecordingState()
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

    private fun setupRecordingActions() {
        binding.recordRoadButton.setOnClickListener {
            if (!MonitoringStateStore.state.value.isMonitoring) {
                showToast(getString(R.string.recording_start_first))
                return@setOnClickListener
            }
            showRecordRoadDialog()
        }

        binding.stopRecordingButton.setOnClickListener {
            lifecycleScope.launch {
                finishRecording()
            }
        }

        binding.cancelRecordingButton.setOnClickListener {
            RoadRecordingStore.cancelRecording()
        }
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

                    binding.correctSpeedButton.visibility =
                        if (state.currentRoadId != null) View.VISIBLE else View.GONE

                    binding.startButton.isEnabled = !state.isMonitoring
                    binding.stopButton.isEnabled = state.isMonitoring
                    binding.recordRoadButton.isEnabled = state.isMonitoring && !RoadRecordingStore.isRecording()
                }
            }
        }
    }

    private fun observeRecordingState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                RoadRecordingStore.state.collect { state ->
                    binding.recordRoadButton.visibility = if (state.isRecording) View.GONE else View.VISIBLE
                    binding.stopRecordingButton.visibility = if (state.isRecording) View.VISIBLE else View.GONE
                    binding.cancelRecordingButton.visibility = if (state.isRecording) View.VISIBLE else View.GONE
                    binding.recordingPointCountText.visibility = if (state.isRecording) View.VISIBLE else View.GONE
                    binding.recordingPointCountText.text = getString(
                        R.string.recording_points,
                        state.pointCount,
                    )
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

    private fun showCorrectSpeedLimitDialog() {
        val state = MonitoringStateStore.state.value
        val roadId = state.currentRoadId ?: return
        val roadName = state.currentRoadName ?: return

        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "e.g. 30"
            state.currentSpeedLimit?.let { setText(it.toString()) }
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.correct_speed_limit_title))
            .setMessage(getString(R.string.correct_speed_limit_message, roadName))
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newLimit = input.text.toString().toIntOrNull()
                if (newLimit != null && newLimit > 0) {
                    SpeedLimitOverrides.setOverride(roadId, newLimit)
                    showToast(getString(R.string.speed_limit_corrected, newLimit, roadName))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showRecordRoadDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
        }

        val roadNameInput = EditText(this).apply {
            hint = getString(R.string.record_road_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }

        val speedLimitInput = EditText(this).apply {
            hint = getString(R.string.record_road_speed_hint)
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        container.addView(roadNameInput)
        container.addView(speedLimitInput)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.record_road_title))
            .setMessage(getString(R.string.recording_prefill_message))
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val speedLimit = speedLimitInput.text.toString().toIntOrNull()
                if (speedLimit == null || speedLimit <= 0) {
                    showToast(getString(R.string.recording_invalid_input))
                    return@setPositiveButton
                }

                val roadName = roadNameInput.text.toString().trim().ifBlank { null }
                RoadRecordingStore.startRecording(roadName, speedLimit)
                showToast(getString(R.string.recording_started))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private suspend fun finishRecording() {
        val draft = RoadRecordingStore.stopRecording()
        if (draft == null) {
            showToast(getString(R.string.recording_too_few_points))
            return
        }

        val existingRoad = withContext(Dispatchers.Default) {
            findLikelyExistingRoad(draft)
        }

        if (existingRoad != null) {
            showReplaceOrAddDialog(draft, existingRoad)
            return
        }

        saveRecordedRoad(draft, replaceRoad = null)
    }

    private suspend fun buildRecordedRoad(draft: RecordedRoadDraft, replaceRoad: Road?): Road {
        val resolvedRoadName = when {
            !draft.roadName.isNullOrBlank() -> draft.roadName
            replaceRoad != null -> replaceRoad.roadName
            else -> {
                showToast(getString(R.string.recording_lookup_started))
                val representativePoint = draft.representativePoint
                val autoName = representativePoint?.let {
                    roadNameResolver.resolveRoadName(it.latitude, it.longitude)
                }
                if (autoName == null) {
                    showToast(getString(R.string.recording_lookup_failed))
                }
                autoName ?: getString(R.string.recording_generated_name, draft.speedLimit)
            }
        }

        return Road(
            roadId = replaceRoad?.roadId ?: "USER_${System.currentTimeMillis()}",
            roadName = resolvedRoadName,
            speedLimit = draft.speedLimit,
            points = draft.points,
        )
    }

    private fun showReplaceOrAddDialog(draft: RecordedRoadDraft, existingRoad: Road) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.recording_existing_road_title))
            .setMessage(
                getString(
                    R.string.recording_existing_road_message,
                    existingRoad.roadName,
                    existingRoad.speedLimit,
                )
            )
            .setPositiveButton(R.string.replace_road) { _, _ ->
                lifecycleScope.launch {
                    saveRecordedRoad(draft, replaceRoad = existingRoad)
                }
            }
            .setNeutralButton(R.string.add_new_road) { _, _ ->
                lifecycleScope.launch {
                    saveRecordedRoad(draft, replaceRoad = null)
                }
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                showToast(getString(R.string.recording_not_saved))
            }
            .show()
    }

    private suspend fun saveRecordedRoad(draft: RecordedRoadDraft, replaceRoad: Road?) {
        val road = buildRecordedRoad(draft, replaceRoad)
        withContext(Dispatchers.IO) {
            UserRoadStorage.saveRoad(road)
        }
        if (replaceRoad != null) {
            SpeedLimitOverrides.removeOverride(replaceRoad.roadId)
        }
        RoadRepository.reload(applicationContext)
        if (MonitoringStateStore.state.value.isMonitoring) {
            LocationService.reloadRoads(this)
        }

        val messageRes = if (replaceRoad != null) {
            R.string.recording_replaced
        } else {
            R.string.recording_saved
        }
        showToast(getString(messageRes, road.roadName, road.points.size))
    }

    private fun findLikelyExistingRoad(draft: RecordedRoadDraft): Road? {
        val samplePoints = samplePoints(draft.points)
        val minimumMatches = maxOf(2, samplePoints.size / 2)

        return RoadRepository.getRoads(applicationContext)
            .mapNotNull { road ->
                val distances = samplePoints.map { point -> distanceToRoadMeters(point, road) }
                val midpointDistance = draft.representativePoint?.let { distanceToRoadMeters(it, road) }
                    ?: Double.MAX_VALUE
                val closeMatches = distances.count { it <= DUPLICATE_POINT_DISTANCE_METERS }
                val averageDistance = distances.average()

                if (midpointDistance <= DUPLICATE_MIDPOINT_DISTANCE_METERS &&
                    closeMatches >= minimumMatches &&
                    averageDistance <= DUPLICATE_AVERAGE_DISTANCE_METERS
                ) {
                    ExistingRoadCandidate(road, averageDistance, midpointDistance)
                } else {
                    null
                }
            }
            .minByOrNull { it.averageDistanceMeters + it.midpointDistanceMeters }
            ?.road
    }

    private fun samplePoints(points: List<GeoPoint>): List<GeoPoint> {
        if (points.size <= MAX_DUPLICATE_CHECK_POINTS) {
            return points
        }

        val step = (points.size - 1).toDouble() / (MAX_DUPLICATE_CHECK_POINTS - 1)
        return buildList {
            for (index in 0 until MAX_DUPLICATE_CHECK_POINTS) {
                add(points[(index * step).toInt().coerceIn(0, points.lastIndex)])
            }
        }
    }

    private fun distanceToRoadMeters(point: GeoPoint, road: Road): Double {
        return road.points.zipWithNext()
            .minOfOrNull { (start, end) ->
                GeoUtils.pointToSegmentDistanceMeters(point, start, end)
            } ?: Double.MAX_VALUE
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private data class ExistingRoadCandidate(
        val road: Road,
        val averageDistanceMeters: Double,
        val midpointDistanceMeters: Double,
    )

    companion object {
        private const val MAX_DUPLICATE_CHECK_POINTS = 7
        private const val DUPLICATE_POINT_DISTANCE_METERS = 20.0
        private const val DUPLICATE_MIDPOINT_DISTANCE_METERS = 20.0
        private const val DUPLICATE_AVERAGE_DISTANCE_METERS = 18.0
    }
}
