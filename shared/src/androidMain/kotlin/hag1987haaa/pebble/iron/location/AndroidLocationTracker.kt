package hag1987haaa.pebble.iron.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import hag1987haaa.pebble.iron.domain.location.LocationTracker
import hag1987haaa.pebble.iron.domain.model.LocationPoint

/**
 * Location tracker built on the platform [LocationManager] so it works on devices
 * without Google Play Services (e.g. GrapheneOS, LineageOS without GApps).
 */
class AndroidLocationTracker(
    private val context: Context
) : LocationTracker {

    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    private var activeListener: LocationListener? = null

    @SuppressLint("MissingPermission")
    override suspend fun getLastKnownLocation(): LocationPoint? = try {
        bestLastKnownLocation()?.toLocationPoint()
    } catch (e: Exception) {
        Log.w("GPS", "Failed to get last location", e)
        null
    }

    @SuppressLint("MissingPermission")
    override fun startTracking(): Flow<LocationPoint> = callbackFlow {
        Log.d("GPS", "startTracking called")

        // 起動直後: 直近の位置情報（2分以内）があれば初期測位として即座に通知し、コールドスタート待ちを解消
        try {
            bestLastKnownLocation()?.let { loc ->
                val ageMs = System.currentTimeMillis() - loc.time
                if (ageMs in 0..120_000L) {
                    Log.d("GPS", "Immediate initial fix from fresh lastLocation (age=${ageMs}ms)")
                    trySend(loc.toLocationPoint())
                }
            }
        } catch (e: Exception) {
            Log.w("GPS", "Immediate lastLocation check skipped", e)
        }

        // Implement every method explicitly: on API < 30 the interface has no
        // default implementations and a missing override would crash.
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                Log.d("GPS", "Location received: ${location.latitude}, ${location.longitude}")
                trySend(location.toLocationPoint())
            }

            override fun onProviderEnabled(provider: String) {
                Log.d("GPS", "Provider enabled: $provider")
            }

            override fun onProviderDisabled(provider: String) {
                Log.w("GPS", "Provider disabled: $provider")
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }

        val provider = trackingProvider()
        if (provider == null) {
            Log.e("GPS", "No location provider available")
        } else {
            try {
                locationManager.requestLocationUpdates(
                    provider,
                    1000L,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
                activeListener = listener
                Log.d("GPS", "Requested location updates from provider=$provider")
            } catch (e: Exception) {
                Log.e("GPS", "Failed to start location updates", e)
            }
        }

        awaitClose {
            Log.d("GPS", "stopTracking (awaitClose)")
            locationManager.removeUpdates(listener)
            if (activeListener == listener) {
                activeListener = null
            }
        }
    }

    override fun stopTracking() {
        activeListener?.let { listener ->
            Log.d("GPS", "Explicit stopTracking called: removing updates")
            try {
                locationManager.removeUpdates(listener)
            } catch (e: Exception) {
                Log.w("GPS", "Error removing location updates in stopTracking", e)
            }
            activeListener = null
        }
    }

    /** GPS for workouts; fall back to network only if GPS is unavailable. */
    private fun trackingProvider(): String? {
        val providers = locationManager.getProviders(true)
        return when {
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
    }

    /** Most recent last-known fix across all enabled providers. */
    @SuppressLint("MissingPermission")
    private fun bestLastKnownLocation(): Location? =
        locationManager.getProviders(true)
            .mapNotNull { p ->
                try {
                    locationManager.getLastKnownLocation(p)
                } catch (e: Exception) {
                    null
                }
            }
            .maxByOrNull { it.elapsedRealtimeNanos }
}

fun Location.toLocationPoint(): LocationPoint = LocationPoint(
    latitude = latitude,
    longitude = longitude,
    altitude = if (hasAltitude()) altitude else null,
    speed = if (hasSpeed()) speed.toDouble() else null,
    bearing = if (hasBearing()) bearing.toDouble() else null,
    accuracy = if (hasAccuracy()) accuracy.toDouble() else null,
    timestamp = if (time > 0L) Instant.fromEpochMilliseconds(time) else Clock.System.now(),
    speedAccuracyMetersPerSecond =
        if (hasSpeedAccuracy()) this.speedAccuracyMetersPerSecond.toDouble() else null,
    bearingAccuracyDegrees =
        if (hasBearingAccuracy()) this.bearingAccuracyDegrees.toDouble() else null,
    verticalAccuracyMeters =
        if (hasVerticalAccuracy()) this.verticalAccuracyMeters.toDouble() else null,
    elapsedRealtimeNanos = elapsedRealtimeNanos.takeIf { it > 0L },
)
