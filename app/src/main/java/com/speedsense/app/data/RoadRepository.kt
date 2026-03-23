package com.speedsense.app.data

import android.content.Context
import com.speedsense.app.matching.RoadMatcher

object RoadRepository {
    @Volatile
    private var cachedRoads: List<Road>? = null

    @Volatile
    private var matcher: RoadMatcher? = null

    fun initialize(context: Context) {
        if (cachedRoads != null && matcher != null) {
            return
        }

        synchronized(this) {
            if (cachedRoads == null || matcher == null) {
                val roads = buildMergedRoads(context)
                cachedRoads = roads
                matcher = RoadMatcher(roads)
            }
        }
    }

    fun reload(context: Context) {
        synchronized(this) {
            val roads = buildMergedRoads(context)
            cachedRoads = roads
            matcher = RoadMatcher(roads)
        }
    }

    fun getMatcher(context: Context): RoadMatcher {
        initialize(context)
        return requireNotNull(matcher) { "Road matcher failed to initialize" }
    }

    fun getRoads(context: Context): List<Road> {
        initialize(context)
        return cachedRoads.orEmpty()
    }

    private fun buildMergedRoads(context: Context): List<Road> {
        val mergedRoads = LinkedHashMap<String, Road>()

        JsonLoader.loadRoads(context.applicationContext).forEach { road ->
            mergedRoads[road.roadId] = road
        }
        UserRoadStorage.loadUserRoads().forEach { road ->
            mergedRoads[road.roadId] = road
        }

        return mergedRoads.values.toList()
    }
}
