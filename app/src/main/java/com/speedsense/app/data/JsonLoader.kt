package com.speedsense.app.data

import android.content.Context
import org.json.JSONArray

object JsonLoader {
    fun loadRoads(context: Context, assetName: String = "roads.json"): List<Road> {
        val jsonText = context.assets.open(assetName).bufferedReader().use { it.readText() }
        val root = JSONArray(jsonText)
        val roads = mutableListOf<Road>()

        for (index in 0 until root.length()) {
            val roadObject = root.optJSONObject(index) ?: continue
            val pointsArray = roadObject.optJSONArray("points") ?: continue

            val points = buildList {
                for (pointIndex in 0 until pointsArray.length()) {
                    val pointArray = pointsArray.optJSONArray(pointIndex) ?: continue
                    if (pointArray.length() < 2) {
                        continue
                    }

                    val latitude = pointArray.optDouble(0, Double.NaN)
                    val longitude = pointArray.optDouble(1, Double.NaN)
                    if (latitude.isNaN() || longitude.isNaN()) {
                        continue
                    }

                    add(GeoPoint(latitude = latitude, longitude = longitude))
                }
            }

            if (points.size < 2) {
                continue
            }

            val speedLimit = roadObject.optInt("speed_limit", 0)
            if (speedLimit <= 0) {
                continue
            }

            roads += Road(
                roadId = roadObject.optString("road_id").ifBlank { "ROAD_$index" },
                roadName = roadObject.optString("road_name").ifBlank { "Unknown road" },
                speedLimit = speedLimit,
                points = points,
            )
        }

        return roads
    }
}

