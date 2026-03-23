package com.speedsense.app.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.speedsense.app.R
import com.speedsense.app.data.RoadRepository
import com.speedsense.app.data.SpeedLimitOverrides
import com.speedsense.app.location.LocationClient
import com.speedsense.app.matching.RoadMatcher
import com.speedsense.app.vibration.VibrationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LocationService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var locationClient: LocationClient
    private lateinit var roadMatcher: RoadMatcher
    private lateinit var vibrationManager: VibrationManager

    private var isTracking = false
    private var lastDetectedRoadId: String? = null
    private var lastSpeedLimit: Int? = null
    private var repeatVibrationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        locationClient = LocationClient(applicationContext)
        roadMatcher = RoadRepository.getMatcher(applicationContext)
        vibrationManager = VibrationManager(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> stopMonitoring()
            ACTION_RELOAD_ROADS -> reloadRoadMatcher()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        val wasTracking = isTracking
        stopLocationUpdates()
        serviceScope.cancel()
        if (wasTracking) {
            MonitoringStateStore.reset()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitoring() {
        if (isTracking) {
            return
        }

        if (!hasLocationPermission()) {
            MonitoringStateStore.postError(getString(R.string.permission_location_required))
            stopSelf()
            return
        }

        startForeground(
            LocationNotificationManager.NOTIFICATION_ID,
            LocationNotificationManager.buildNotification(this, lastSpeedLimit),
        )

        isTracking = true
        MonitoringStateStore.setMonitoring(true)

        locationClient.startLocationUpdates { location ->
            serviceScope.launch {
                val speedMph = if (location.hasSpeed()) location.speed * 2.23694f else 0f
                MonitoringStateStore.updateLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    speedMph = speedMph,
                    timeMs = System.currentTimeMillis(),
                )

                if (RoadRecordingStore.isRecording()) {
                    RoadRecordingStore.addPoint(location.latitude, location.longitude)
                }

                val match = roadMatcher.findNearestRoad(location) ?: return@launch
                lastDetectedRoadId = match.roadId
                val effectiveLimit = SpeedLimitOverrides.getOverride(match.roadId) ?: match.speedLimit

                if (effectiveLimit != lastSpeedLimit) {
                    lastSpeedLimit = effectiveLimit
                    vibrationManager.vibrateForSpeedLimit(effectiveLimit)
                    LocationNotificationManager.updateNotification(this@LocationService, effectiveLimit)
                    startRepeatingVibration(effectiveLimit)
                }

                MonitoringStateStore.updateMatch(effectiveLimit, match.roadId, match.roadName)
            }
        }
    }

    private fun stopMonitoring() {
        stopLocationUpdates()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        MonitoringStateStore.reset()
    }

    private fun startRepeatingVibration(speedLimit: Int) {
        repeatVibrationJob?.cancel()
        repeatVibrationJob = serviceScope.launch {
            while (true) {
                delay(VIBRATION_REPEAT_INTERVAL_MS)
                if (lastSpeedLimit == speedLimit) {
                    vibrationManager.vibrateForSpeedLimit(speedLimit)
                } else {
                    break
                }
            }
        }
    }

    private fun stopLocationUpdates() {
        if (!isTracking) {
            return
        }
        isTracking = false
        repeatVibrationJob?.cancel()
        repeatVibrationJob = null
        vibrationManager.cancel()
        locationClient.stopLocationUpdates()
    }

    private fun reloadRoadMatcher() {
        RoadRepository.reload(applicationContext)
        roadMatcher = RoadRepository.getMatcher(applicationContext)
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val ACTION_START = "com.speedsense.app.action.START"
        private const val ACTION_STOP = "com.speedsense.app.action.STOP"
        private const val ACTION_RELOAD_ROADS = "com.speedsense.app.action.RELOAD_ROADS"
        private const val VIBRATION_REPEAT_INTERVAL_MS = 5_000L

        fun start(context: Context) {
            val intent = Intent(context, LocationService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun reloadRoads(context: Context) {
            val intent = Intent(context, LocationService::class.java).apply {
                action = ACTION_RELOAD_ROADS
            }
            context.startService(intent)
        }
    }
}
