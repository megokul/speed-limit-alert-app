package com.speedsense.app.service

import com.speedsense.app.data.GeoPoint
import com.speedsense.app.data.Road
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs

data class RecordingState(
    val isRecording: Boolean = false,
    val roadName: String? = null,
    val speedLimit: Int? = null,
    val pointCount: Int = 0,
)

data class RecordedRoadDraft(
    val roadName: String?,
    val speedLimit: Int,
    val points: List<GeoPoint>,
) {
    val representativePoint: GeoPoint?
        get() = points.getOrNull(points.size / 2)
}

object RoadRecordingStore {
    private val mutableState = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = mutableState.asStateFlow()

    private val collectedPoints = mutableListOf<GeoPoint>()

    fun startRecording(roadName: String?, speedLimit: Int) {
        collectedPoints.clear()
        mutableState.value = RecordingState(
            isRecording = true,
            roadName = roadName?.takeIf { it.isNotBlank() },
            speedLimit = speedLimit,
            pointCount = 0,
        )
    }

    fun addPoint(latitude: Double, longitude: Double) {
        if (!mutableState.value.isRecording) return

        val last = collectedPoints.lastOrNull()
        if (last != null && abs(last.latitude - latitude) < 0.00005 && abs(last.longitude - longitude) < 0.00005) {
            return
        }

        collectedPoints.add(GeoPoint(latitude, longitude))
        mutableState.update { it.copy(pointCount = collectedPoints.size) }
    }

    fun stopRecording(): RecordedRoadDraft? {
        val state = mutableState.value
        if (!state.isRecording) return null

        val points = collectedPoints.toList()
        val limit = state.speedLimit ?: return null

        cancelRecording()

        if (points.size < 2) return null

        return RecordedRoadDraft(
            roadName = state.roadName,
            speedLimit = limit,
            points = points,
        )
    }

    fun cancelRecording() {
        collectedPoints.clear()
        mutableState.value = RecordingState()
    }

    fun isRecording(): Boolean = mutableState.value.isRecording
}
