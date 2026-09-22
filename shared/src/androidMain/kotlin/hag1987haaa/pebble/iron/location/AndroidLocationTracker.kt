package hag1987haaa.pebble.iron.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
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

class AndroidLocationTracker(
    private val context: Context
) : LocationTracker {

    private val client: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    @SuppressLint("MissingPermission")
    override suspend fun getLastKnownLocation(): LocationPoint? = suspendCancellableCoroutine { cont ->
        try {
            client.lastLocation
                .addOnSuccessListener { loc ->
                    cont.resume(loc?.toLocationPoint())
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
        } catch (e: Exception) {
            Log.w("GPS", "Failed to get last location", e)
            cont.resume(null)
        }
    }

    @SuppressLint("MissingPermission")
    override fun startTracking(): Flow<LocationPoint> = callbackFlow {
        Log.d("GPS", "startTracking called")

        // 起動直後: 直近の位置情報（2分以内）があれば初期測位として即座に通知し、コールドスタート待ちを解消
        try {
            client.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    val ageMs = System.currentTimeMillis() - loc.time
                    if (ageMs in 0..120_000L) {
                        Log.d("GPS", "Immediate initial fix from fresh lastLocation (age=${ageMs}ms)")
                        trySend(loc.toLocationPoint())
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("GPS", "Immediate lastLocation check skipped", e)
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateDistanceMeters(0f)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                val location = result.locations.lastOrNull()
                if (location != null) {
                    Log.d("GPS", "Location received: ${location.latitude}, ${location.longitude}")
                    trySend(location.toLocationPoint())
                } else {
                    Log.d("GPS", "Location received but was null")
                }
            }
        }

        client.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        ).addOnFailureListener { e ->
            Log.e("GPS", "Failed to start location updates", e)
        }

        awaitClose {
            Log.d("GPS", "stopTracking (awaitClose)")
            client.removeLocationUpdates(locationCallback)
        }
    }

    override fun stopTracking() {}
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
