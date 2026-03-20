package com.speedsense.app.matching

import android.location.Location
import com.speedsense.app.data.GeoPoint
import com.speedsense.app.data.Road
import com.speedsense.app.data.RoadSegment
import com.speedsense.app.utils.GeoUtils
import kotlin.math.floor

class RoadMatcher(
    roads: List<Road>,
) {
    private val spatialIndex: Map<CellKey, List<RoadSegment>> = buildSpatialIndex(roads)

    fun findNearestRoad(location: Location): RoadMatch? {
        val origin = GeoPoint(location.latitude, location.longitude)
        val nearbySegments = getCandidateSegments(origin)
        if (nearbySegments.isEmpty()) {
            return null
        }

        val bestMatch = nearbySegments
            .map { segment ->
                val distanceMeters = GeoUtils.pointToSegmentDistanceMeters(origin, segment.start, segment.end)
                val headingPenalty = if (location.hasBearing()) {
                    GeoUtils.orientationPenaltyMeters(location.bearing.toDouble(), segment.start, segment.end)
                } else {
                    0.0
                }

                SegmentScore(
                    segment = segment,
                    distanceMeters = distanceMeters,
                    score = distanceMeters + headingPenalty,
                )
            }
            .filter { it.distanceMeters < MAX_MATCH_DISTANCE_METERS }
            .minByOrNull { it.score }
            ?: return null

        return RoadMatch(
            roadId = bestMatch.segment.roadId,
            roadName = bestMatch.segment.roadName,
            speedLimit = bestMatch.segment.speedLimit,
            distanceMeters = bestMatch.distanceMeters,
        )
    }

    private fun getCandidateSegments(origin: GeoPoint): Set<RoadSegment> {
        val center = cellKeyFor(origin)
        val candidates = LinkedHashSet<RoadSegment>()

        for (latOffset in -1..1) {
            for (lonOffset in -1..1) {
                val key = CellKey(center.latCell + latOffset, center.lonCell + lonOffset)
                spatialIndex[key].orEmpty().forEach(candidates::add)
            }
        }

        return candidates
    }

    private fun buildSpatialIndex(roads: List<Road>): Map<CellKey, List<RoadSegment>> {
        val buckets = mutableMapOf<CellKey, MutableList<RoadSegment>>()

        roads.forEach { road ->
            road.points.zipWithNext().forEach { (start, end) ->
                val segment = RoadSegment(
                    roadId = road.roadId,
                    roadName = road.roadName,
                    speedLimit = road.speedLimit,
                    start = start,
                    end = end,
                )

                val minLat = minOf(start.latitude, end.latitude) - SEARCH_MARGIN_DEGREES
                val maxLat = maxOf(start.latitude, end.latitude) + SEARCH_MARGIN_DEGREES
                val minLon = minOf(start.longitude, end.longitude) - SEARCH_MARGIN_DEGREES
                val maxLon = maxOf(start.longitude, end.longitude) + SEARCH_MARGIN_DEGREES

                val latStart = floor(minLat / GRID_SIZE_DEGREES).toInt()
                val latEnd = floor(maxLat / GRID_SIZE_DEGREES).toInt()
                val lonStart = floor(minLon / GRID_SIZE_DEGREES).toInt()
                val lonEnd = floor(maxLon / GRID_SIZE_DEGREES).toInt()

                for (latCell in latStart..latEnd) {
                    for (lonCell in lonStart..lonEnd) {
                        buckets.getOrPut(CellKey(latCell, lonCell)) { mutableListOf() }
                            .add(segment)
                    }
                }
            }
        }

        return buckets
    }

    private fun cellKeyFor(point: GeoPoint): CellKey {
        return CellKey(
            latCell = floor(point.latitude / GRID_SIZE_DEGREES).toInt(),
            lonCell = floor(point.longitude / GRID_SIZE_DEGREES).toInt(),
        )
    }

    private data class CellKey(
        val latCell: Int,
        val lonCell: Int,
    )

    private data class SegmentScore(
        val segment: RoadSegment,
        val distanceMeters: Double,
        val score: Double,
    )

    companion object {
        private const val MAX_MATCH_DISTANCE_METERS = 30.0
        private const val GRID_SIZE_DEGREES = 0.001
        private const val SEARCH_MARGIN_DEGREES = 0.00035
    }
}

data class RoadMatch(
    val roadId: String,
    val roadName: String,
    val speedLimit: Int,
    val distanceMeters: Double,
)

