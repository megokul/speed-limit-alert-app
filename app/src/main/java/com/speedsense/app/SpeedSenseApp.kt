package com.speedsense.app

import android.app.Application
import com.speedsense.app.data.RoadRepository
import com.speedsense.app.data.SpeedLimitOverrides
import com.speedsense.app.data.UserRoadStorage
import com.speedsense.app.service.LocationNotificationManager

class SpeedSenseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LocationNotificationManager.createChannel(this)
        SpeedLimitOverrides.init(this)
        UserRoadStorage.init(this)
        RoadRepository.initialize(this)
    }
}

