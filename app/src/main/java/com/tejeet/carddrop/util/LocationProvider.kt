package com.tejeet.carddrop.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Where a card was scanned. Uses the platform LocationManager (no Play Services dependency):
 * the freshest recent fix if there is one, otherwise a single update with a short timeout.
 * Everything here returns null rather than failing - location is always optional.
 */
object LocationProvider {

    data class Fix(val latitude: Double, val longitude: Double, val city: String, val country: String)

    private const val MAX_AGE_MS = 10 * 60 * 1000L
    private const val UPDATE_TIMEOUT_MS = 12_000L

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun current(context: Context): Fix? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val location = lastKnown(manager) ?: singleUpdate(context, manager) ?: return null
        val (city, country) = reverseGeocode(context, location.latitude, location.longitude)
        return Fix(location.latitude, location.longitude, city, country)
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager): Location? {
        val now = System.currentTimeMillis()
        return runCatching {
            manager.getProviders(true)
                .mapNotNull { manager.getLastKnownLocation(it) }
                .filter { now - it.time < MAX_AGE_MS }
                .maxByOrNull { it.time }
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private suspend fun singleUpdate(context: Context, manager: LocationManager): Location? {
        val provider = when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return null
        }
        return withTimeoutOrNull(UPDATE_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        runCatching { manager.removeUpdates(this) }
                        if (continuation.isActive) continuation.resume(location)
                    }

                    // Required on API < 30.
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                runCatching {
                    manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                }.onFailure { if (continuation.isActive) continuation.resume(null) }
                continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
            }
        }
    }

    /** City and country for a position; empty strings when the device can't resolve them offline. */
    private suspend fun reverseGeocode(context: Context, lat: Double, lng: Double): Pair<String, String> =
        withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                val address = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)?.firstOrNull()
                val city = address?.locality ?: address?.subAdminArea ?: address?.adminArea ?: ""
                val country = address?.countryName ?: ""
                city to country
            }.getOrDefault("" to "")
        }
}
