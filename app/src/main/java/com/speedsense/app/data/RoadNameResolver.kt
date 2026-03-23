package com.speedsense.app.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume

class RoadNameResolver(
    private val context: Context,
) {
    suspend fun resolveRoadName(latitude: Double, longitude: Double): String? {
        val cacheKey = cacheKey(latitude, longitude)
        synchronized(cache) {
            cache[cacheKey]?.let { return it }
        }

        val resolvedName = resolveWithAndroidGeocoder(latitude, longitude)
            ?: resolveWithNominatim(latitude, longitude)

        synchronized(cache) {
            cache[cacheKey] = resolvedName
        }
        return resolvedName
    }

    private suspend fun resolveWithAndroidGeocoder(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) {
            return null
        }

        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.awaitFromLocation(latitude, longitude)
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1).orEmpty().firstOrNull()
                }
            }

            address?.bestRoadName()
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private suspend fun resolveWithNominatim(latitude: Double, longitude: Double): String? {
        val waitTimeMs = synchronized(requestLock) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastNominatimRequestMs
            val remaining = (NOMINATIM_MIN_INTERVAL_MS - elapsed).coerceAtLeast(0L)
            lastNominatimRequestMs = now + remaining
            remaining
        }
        if (waitTimeMs > 0) {
            delay(waitTimeMs)
        }

        return withContext(Dispatchers.IO) {
            val encodedLanguage = URLEncoder.encode(Locale.getDefault().toLanguageTag(), Charsets.UTF_8.name())
            val url = URL(
                "$NOMINATIM_REVERSE_URL?format=jsonv2&lat=$latitude&lon=$longitude&zoom=18&addressdetails=1&accept-language=$encodedLanguage"
            )

            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            try {
                if (connection.responseCode !in 200..299) {
                    return@withContext null
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(body)
                val address = root.optJSONObject("address")
                address?.optString("road").takeUnless { it.isNullOrBlank() }
                    ?: address?.optString("pedestrian").takeUnless { it.isNullOrBlank() }
                    ?: address?.optString("cycleway").takeUnless { it.isNullOrBlank() }
                    ?: address?.optString("footway").takeUnless { it.isNullOrBlank() }
                    ?: root.optString("name").takeUnless { it.isBlank() }
            } catch (_: IOException) {
                null
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun Address.bestRoadName(): String? {
        return thoroughfare
            ?: subThoroughfare
            ?: featureName
            ?: getAddressLine(0)
    }

    private suspend fun Geocoder.awaitFromLocation(latitude: Double, longitude: Double): Address? {
        return suspendCancellableCoroutine { continuation ->
            getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    if (!continuation.isActive) return
                    continuation.resume(addresses.firstOrNull())
                }

                override fun onError(errorMessage: String?) {
                    if (!continuation.isActive) return
                    continuation.resume(null)
                }
            })
        }
    }

    private fun cacheKey(latitude: Double, longitude: Double): String {
        return "%1$.5f,%2$.5f".format(Locale.US, latitude, longitude)
    }

    companion object {
        private const val NOMINATIM_REVERSE_URL = "https://nominatim.openstreetmap.org/reverse"
        private const val USER_AGENT = "SpeedSense/1.0 (road-name lookup; Android)"
        private const val NOMINATIM_MIN_INTERVAL_MS = 1_000L
        private val requestLock = Any()
        private var lastNominatimRequestMs = 0L
        private val cache = linkedMapOf<String, String?>()
    }
}
