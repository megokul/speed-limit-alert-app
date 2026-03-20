package com.speedsense.app.utils

import com.speedsense.app.data.GeoPoint
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object GeoUtils {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun haversineDistanceMeters(start: GeoPoint, end: GeoPoint): Double {
        val latitudeDelta = Math.toRadians(end.latitude - start.latitude)
        val longitudeDelta = Math.toRadians(end.longitude - start.longitude)
        val startLatitude = Math.toRadians(start.latitude)
        val endLatitude = Math.toRadians(end.latitude)

        val a = sin(latitudeDelta / 2).let { it * it } +
            cos(startLatitude) * cos(endLatitude) * sin(longitudeDelta / 2).let { it * it }

        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
    }

    fun pointToSegmentDistanceMeters(point: GeoPoint, start: GeoPoint, end: GeoPoint): Double {
        val projectedStart = localProjectionMeters(point, start)
        val projectedEnd = localProjectionMeters(point, end)

        val segmentX = projectedEnd.first - projectedStart.first
        val segmentY = projectedEnd.second - projectedStart.second
        val segmentLengthSquared = (segmentX * segmentX) + (segmentY * segmentY)

        if (segmentLengthSquared == 0.0) {
            return sqrt((projectedStart.first * projectedStart.first) + (projectedStart.second * projectedStart.second))
        }

        val projection = ((-projectedStart.first * segmentX) + (-projectedStart.second * segmentY)) / segmentLengthSquared
        val clampedProjection = projection.coerceIn(0.0, 1.0)

        val closestX = projectedStart.first + (segmentX * clampedProjection)
        val closestY = projectedStart.second + (segmentY * clampedProjection)
        return sqrt((closestX * closestX) + (closestY * closestY))
    }

    fun orientationPenaltyMeters(locationBearing: Double, start: GeoPoint, end: GeoPoint): Double {
        val segmentBearing = bearingDegrees(start, end)
        val directDifference = angularDifferenceDegrees(locationBearing, segmentBearing)
        val reverseDifference = angularDifferenceDegrees(locationBearing, normalizeBearing(segmentBearing + 180.0))
        val smallestDifference = min(directDifference, reverseDifference)
        return smallestDifference / 12.0
    }

    private fun localProjectionMeters(origin: GeoPoint, point: GeoPoint): Pair<Double, Double> {
        val meanLatitudeRadians = Math.toRadians((origin.latitude + point.latitude) / 2.0)
        val x = Math.toRadians(point.longitude - origin.longitude) * EARTH_RADIUS_METERS * cos(meanLatitudeRadians)
        val y = Math.toRadians(point.latitude - origin.latitude) * EARTH_RADIUS_METERS
        return x to y
    }

    private fun bearingDegrees(start: GeoPoint, end: GeoPoint): Double {
        val startLatitude = Math.toRadians(start.latitude)
        val endLatitude = Math.toRadians(end.latitude)
        val longitudeDelta = Math.toRadians(end.longitude - start.longitude)

        val y = sin(longitudeDelta) * cos(endLatitude)
        val x = cos(startLatitude) * sin(endLatitude) -
            sin(startLatitude) * cos(endLatitude) * cos(longitudeDelta)

        return normalizeBearing(Math.toDegrees(atan2(y, x)))
    }

    private fun angularDifferenceDegrees(first: Double, second: Double): Double {
        val difference = abs(normalizeBearing(first) - normalizeBearing(second))
        return min(difference, 360.0 - difference)
    }

    private fun normalizeBearing(bearing: Double): Double {
        val normalized = bearing % 360.0
        return if (normalized < 0) normalized + 360.0 else normalized
    }
}

