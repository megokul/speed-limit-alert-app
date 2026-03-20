package com.speedsense.app.data

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

data class Road(
    val roadId: String,
    val roadName: String,
    val speedLimit: Int,
    val points: List<GeoPoint>,
)

data class RoadSegment(
    val roadId: String,
    val roadName: String,
    val speedLimit: Int,
    val start: GeoPoint,
    val end: GeoPoint,
)

