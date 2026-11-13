package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.tan

data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String
)

object LocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentRealLocation(context: Context): DeviceLocation? {
        if (!hasLocationPermission(context)) return null

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        var location: Location? = null

        try {
            val cts = CancellationTokenSource()
            location = fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }

        if (location == null) {
            try {
                location = fusedClient.lastLocation.await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }

        if (location == null) {
            location = getFallbackLocationManagerFix(context)
        }

        val loc = location ?: return null
        val addressLabel = reverseGeocodeLabel(context, loc.latitude, loc.longitude)
        return DeviceLocation(
            latitude = loc.latitude,
            longitude = loc.longitude,
            label = addressLabel
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun getFallbackLocationManagerFix(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        for (p in providers) {
            try {
                if (lm.isProviderEnabled(p)) {
                    lm.getLastKnownLocation(p)?.let { return it }
                }
            } catch (_: Exception) {
            }
        }

        return suspendCancellableCoroutine { cont ->
            val provider = when {
                runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) -> LocationManager.GPS_PROVIDER
                runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }

            val listener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    lm.removeUpdates(this)
                    if (cont.isActive) cont.resume(loc)
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            try {
                lm.requestLocationUpdates(provider, 1000L, 0f, listener, Looper.getMainLooper())
            } catch (_: Exception) {
                if (cont.isActive) cont.resume(null)
            }
            cont.invokeOnCancellation {
                runCatching { lm.removeUpdates(listener) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun observeLiveLocationUpdates(context: Context, intervalMs: Long = 10_000L): Flow<Location> = callbackFlow {
        if (!hasLocationPermission(context)) {
            close(SecurityException("Location permission not granted"))
            return@callbackFlow
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener {
                // Fallback to LocationManager if Play Services unavailable
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val provider = LocationManager.GPS_PROVIDER
                if (lm != null && runCatching { lm.isProviderEnabled(provider) }.getOrDefault(false)) {
                    runCatching {
                        lm.requestLocationUpdates(provider, intervalMs, 5f, { loc -> trySend(loc) }, Looper.getMainLooper())
                    }
                }
            }

        awaitClose {
            fusedClient.removeLocationUpdates(callback)
        }
    }

    suspend fun reverseGeocodeLabel(context: Context, lat: Double, lng: Double): String {
        val fallback = String.format(Locale.US, "%.5f, %.5f", lat, lng)
        return withContext(Dispatchers.IO) {
            try {
                if (!Geocoder.isPresent()) return@withContext fallback
                val geocoder = Geocoder(context, Locale("ar"))
                @Suppress("DEPRECATION")
                val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(lat, lng, 1) { addresses ->
                            if (cont.isActive) cont.resume(addresses)
                        }
                    }
                } else {
                    geocoder.getFromLocation(lat, lng, 1)
                }
                val addr = list?.firstOrNull()
                val parts = listOfNotNull(
                    addr?.subLocality ?: addr?.thoroughfare,
                    addr?.locality ?: addr?.subAdminArea,
                    addr?.countryName
                ).filter { it.isNotBlank() }.distinct()
                if (parts.isNotEmpty()) parts.joinToString("، ") else fallback
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                fallback
            }
        }
    }

    /**
     * Computes the OpenStreetMap slippy tile URL for rendering an embedded map preview card.
     */
    fun osmTileUrl(lat: Double, lng: Double, zoom: Int = 15): String {
        val clampedLat = lat.coerceIn(-85.0511, 85.0511)
        val n = 1 shl zoom
        val xTile = floor((lng + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
        val latRad = Math.toRadians(clampedLat)
        val yTile = floor((1.0 - asinh(tan(latRad)) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
        return "https://tile.openstreetmap.org/$zoom/$xTile/$yTile.png"
    }

    fun openInMapsApp(context: Context, lat: Double, lng: Double, label: String) {
        val encodedLabel = Uri.encode(label.ifBlank { "الموقع" })
        val geoUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($encodedLabel)")
        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
                return
            }
        } catch (_: Exception) {
        }
        // Browser fallback if no maps app is installed
        val webUri = Uri.parse("https://maps.google.com/?q=$lat,$lng")
        val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(webIntent) }
    }
}
