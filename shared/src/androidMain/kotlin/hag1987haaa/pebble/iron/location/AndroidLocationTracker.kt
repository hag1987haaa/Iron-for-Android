package hag1987haaa.pebble.iron.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import hag1987haaa.pebble.iron.domain.location.LocationTracker
import hag1987haaa.pebble.iron.domain.model.LocationPoint

/**
 * Uses the Play Services fused location provider when Google Play Services is available,
 * and falls back to the platform [LocationManager] when it is not (e.g. GrapheneOS or
 * LineageOS without GApps) or when fused location fails to start.
 */
class AndroidLocationTracker(
    private val context: Context
) : LocationTracker {

    private val client: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }
    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    /** Checked once: Play Services doesn't come and go while the app is running. */
    private val useFused: Boolean by lazy {
        val available = try {
            GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        } catch (e: Exception) {
            Log.w("GPS", "Play Services check failed", e)
            false
        }
        Log.d("GPS", if (available) "Using fused location" else "Play Services unavailable, using LocationManager")
        available
    }

    /** Removes whichever updates (fused or platform) are currently active. */
    private var activeStop: (() -> Unit)? = null

    @SuppressLint("MissingPermission")
    override suspend fun getLastKnownLocation(): LocationPoint? {
        if (useFused) {
            val fused = suspendCancellableCoroutine<Location?> { cont ->
                try {
                    client.lastLocation
                        .addOnSuccessListener { loc -> cont.resume(loc) }
                        .addOnFailureListener { cont.resume(null) }
                } catch (e: Exception) {
                    Log.w("GPS", "Failed to get last location", e)
                    cont.resume(null)
                }
            }
            if (fused != null) return fused.toLocationPoint()
        }
        return try {
            bestLastKnownLocation()?.toLocationPoint()
        } catch (e: Exception) {
            Log.w("GPS", "Failed to get last location", e)
            null
        }
    }

    @SuppressLint("MissingPermission")
    override fun startTracking(): Flow<LocationPoint> = callbackFlow {
        Log.d("GPS", "startTracking called")

        // 起動直後: 直近の位置情報（2分以内）があれば初期測位として即座に通知し、コールドスタート待ちを解消
        val sendIfFresh: (Location?) -> Unit = { loc ->
            if (loc != null) {
                val ageMs = System.currentTimeMillis() - loc.time
                if (ageMs in 0..120_000L) {
                    Log.d("GPS", "Immediate initial fix from fresh lastLocation (age=${ageMs}ms)")
                    trySend(loc.toLocationPoint())
                }
            }
        }
        try {
            if (useFused) {
                client.lastLocation.addOnSuccessListener { loc -> sendIfFresh(loc) }
            } else {
                sendIfFresh(bestLastKnownLocation())
            }
        } catch (e: Exception) {
            Log.w("GPS", "Immediate lastLocation check skipped", e)
        }

        var stop: () -> Unit = {}
        fun startPlatform() {
            stop = startPlatformUpdates { trySend(it.toLocationPoint()) }
            activeStop = stop
        }

        if (useFused) {
            stop = startFusedUpdates(
                onLocation = { trySend(it.toLocationPoint()) },
                onFailure = {
                    // Fused failed to start (e.g. Play Services present but broken):
                    // fall back instead of tracking nothing.
                    if (!isClosedForSend) {
                        Log.w("GPS", "Falling back to LocationManager")
                        startPlatform()
                    }
                },
            )
            activeStop = stop
        } else {
            startPlatform()
        }

        awaitClose {
            Log.d("GPS", "stopTracking (awaitClose)")
            stop()
            if (activeStop == stop) {
                activeStop = null
            }
        }
    }

    override fun stopTracking() {
        activeStop?.let { stop ->
            Log.d("GPS", "Explicit stopTracking called: removing updates")
            try {
                stop()
            } catch (e: Exception) {
                Log.w("GPS", "Error removing location updates in stopTracking", e)
            }
            activeStop = null
        }
    }

    /** Starts fused updates; returns a function that removes them. */
    @SuppressLint("MissingPermission")
    private fun startFusedUpdates(
        onLocation: (Location) -> Unit,
        onFailure: () -> Unit,
    ): () -> Unit {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateDistanceMeters(0f)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                val location = result.locations.lastOrNull()
                if (location != null) {
                    Log.d("GPS", "Location received: ${location.latitude}, ${location.longitude}")
                    onLocation(location)
                } else {
                    Log.d("GPS", "Location received but was null")
                }
            }
        }

        val remove = { client.removeLocationUpdates(locationCallback); Unit }
        try {
            client.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            ).addOnFailureListener { e ->
                Log.e("GPS", "Failed to start location updates", e)
                remove()
                onFailure()
            }
        } catch (e: Exception) {
            Log.e("GPS", "Failed to start location updates", e)
            onFailure()
        }
        return remove
    }

    /** Starts platform [LocationManager] updates; returns a function that removes them. */
    @SuppressLint("MissingPermission")
    private fun startPlatformUpdates(onLocation: (Location) -> Unit): () -> Unit {
        // Implement every method explicitly: on API < 30 the interface has no
        // default implementations and a missing override would crash.
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                Log.d("GPS", "Location received: ${location.latitude}, ${location.longitude}")
                onLocation(location)
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
            return {}
        }
        try {
            locationManager.requestLocationUpdates(
                provider,
                1000L,
                0f,
                listener,
                Looper.getMainLooper()
            )
            Log.d("GPS", "Requested location updates from provider=$provider")
        } catch (e: Exception) {
            Log.e("GPS", "Failed to start location updates", e)
            return {}
        }
        return { locationManager.removeUpdates(listener) }
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
