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
import kotlinx.datetime.Clock
import hag1987haaa.pebble.iron.domain.location.LocationTracker
import hag1987haaa.pebble.iron.domain.model.LocationPoint

class AndroidLocationTracker(
    private val context: Context
) : LocationTracker {

    private val client: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    @SuppressLint("MissingPermission")
    override fun startTracking(): Flow<LocationPoint> = callbackFlow {
        Log.d("GPS", "startTracking called")
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
    timestamp = Clock.System.now()
)
