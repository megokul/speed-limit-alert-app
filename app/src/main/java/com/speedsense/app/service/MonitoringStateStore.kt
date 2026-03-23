package com.speedsense.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MonitoringState(
    val isMonitoring: Boolean = false,
    val currentSpeedLimit: Int? = null,
    val currentRoadId: String? = null,
    val currentRoadName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val speedMph: Float? = null,
    val lastUpdateTimeMs: Long? = null,
    val errorMessage: String? = null,
)

object MonitoringStateStore {
    private val mutableState = MutableStateFlow(MonitoringState())
    val state: StateFlow<MonitoringState> = mutableState.asStateFlow()

    fun setMonitoring(isMonitoring: Boolean) {
        mutableState.update { current ->
            current.copy(isMonitoring = isMonitoring, errorMessage = null)
        }
    }

    fun updateLocation(latitude: Double, longitude: Double, speedMph: Float, timeMs: Long) {
        mutableState.update { current ->
            current.copy(
                latitude = latitude,
                longitude = longitude,
                speedMph = speedMph,
                lastUpdateTimeMs = timeMs,
            )
        }
    }

    fun updateMatch(speedLimit: Int, roadId: String, roadName: String) {
        mutableState.update { current ->
            current.copy(
                currentSpeedLimit = speedLimit,
                currentRoadId = roadId,
                currentRoadName = roadName,
                errorMessage = null,
            )
        }
    }

    fun postError(message: String) {
        mutableState.update { current ->
            current.copy(errorMessage = message, isMonitoring = false)
        }
    }

    fun reset() {
        mutableState.value = MonitoringState()
    }
}

