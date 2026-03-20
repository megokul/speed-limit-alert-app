package com.speedsense.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class LocationClient(
    context: Context,
) {
    private val fusedLocationProviderClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var locationCallback: LocationCallback? = null

    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
        .setMinUpdateIntervalMillis(UPDATE_INTERVAL_MS)
        .setMaxUpdateDelayMillis(MAX_UPDATE_DELAY_MS)
        .build()

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(onLocation: (Location) -> Unit) {
        if (locationCallback != null) {
            return
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let(onLocation)
            }
        }

        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest,
            requireNotNull(locationCallback),
            Looper.getMainLooper(),
        )
    }

    fun stopLocationUpdates() {
        locationCallback?.let(fusedLocationProviderClient::removeLocationUpdates)
        locationCallback = null
    }

    companion object {
        const val UPDATE_INTERVAL_MS = 2_000L
        private const val MAX_UPDATE_DELAY_MS = 4_000L
    }
}

