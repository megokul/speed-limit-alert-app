package com.speedsense.app.data

import android.content.Context
import android.content.SharedPreferences

object SpeedLimitOverrides {
    private const val PREFS_NAME = "speed_limit_overrides"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getOverride(roadId: String): Int? {
        val value = prefs.getInt(roadId, -1)
        return if (value > 0) value else null
    }

    fun setOverride(roadId: String, speedLimit: Int) {
        prefs.edit().putInt(roadId, speedLimit).apply()
    }

    fun removeOverride(roadId: String) {
        prefs.edit().remove(roadId).apply()
    }
}
