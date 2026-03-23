package com.speedsense.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object UserRoadStorage {
    private lateinit var file: File

    fun init(context: Context) {
        file = File(context.applicationContext.filesDir, "user_roads.json")
    }

    fun loadUserRoads(): List<Road> {
        if (!file.exists()) return emptyList()

        val jsonText = file.readText()
        if (jsonText.isBlank()) return emptyList()

        val root = JSONArray(jsonText)
        val roads = mutableListOf<Road>()

        for (i in 0 until root.length()) {
            val obj = root.optJSONObject(i) ?: continue
            val pointsArray = obj.optJSONArray("points") ?: continue

            val points = buildList {
                for (j in 0 until pointsArray.length()) {
                    val p = pointsArray.optJSONArray(j) ?: continue
                    if (p.length() < 2) continue
                    val lat = p.optDouble(0, Double.NaN)
                    val lon = p.optDouble(1, Double.NaN)
                    if (lat.isNaN() || lon.isNaN()) continue
                    add(GeoPoint(lat, lon))
                }
            }

            if (points.size < 2) continue
            val speedLimit = obj.optInt("speed_limit", 0)
            if (speedLimit <= 0) continue

            roads += Road(
                roadId = obj.optString("road_id").ifBlank { "USER_$i" },
                roadName = obj.optString("road_name").ifBlank { "Unknown road" },
                speedLimit = speedLimit,
                points = points,
            )
        }

        return roads
    }

    @Synchronized
    fun saveRoad(road: Road) {
        val existing = if (file.exists() && file.readText().isNotBlank()) {
            JSONArray(file.readText())
        } else {
            JSONArray()
        }

        val pointsArray = JSONArray().apply {
            road.points.forEach { point ->
                put(JSONArray().apply {
                    put(point.latitude)
                    put(point.longitude)
                })
            }
        }

        val roadObj = JSONObject().apply {
            put("road_id", road.roadId)
            put("road_name", road.roadName)
            put("speed_limit", road.speedLimit)
            put("points", pointsArray)
        }

        val updated = JSONArray()
        for (index in 0 until existing.length()) {
            val existingRoad = existing.optJSONObject(index) ?: continue
            if (existingRoad.optString("road_id") == road.roadId) {
                continue
            }
            updated.put(existingRoad)
        }

        updated.put(roadObj)
        file.writeText(updated.toString(2))
    }
}
