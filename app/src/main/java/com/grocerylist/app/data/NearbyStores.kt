package com.grocerylist.app.data

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import kotlin.math.roundToLong

data class NearbyStore(val name: String, val distanceMeters: Int)

object NearbyStores {
    // Stores don't move, so one lookup per half hour is plenty while the app is open.
    private const val CACHE_MS = 30 * 60 * 1000L
    private var cachedAt = 0L

    var cached: List<NearbyStore>? = null
        get() = field.takeIf { System.currentTimeMillis() - cachedAt < CACHE_MS }
        private set

    suspend fun findCached(context: Context): List<NearbyStore> =
        cached ?: find(context).also {
            cached = it
            cachedAt = System.currentTimeMillis()
        }

    /** Caller must hold ACCESS_COARSE_LOCATION. Throws if the location or the lookup isn't available. */
    @SuppressLint("MissingPermission")
    suspend fun find(context: Context): List<NearbyStore> {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val location = client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
            ?: client.lastLocation.await()
            ?: error("Location unavailable")
        val response = ServerApi.post(
            "/api/stores",
            // Two decimal places is about 1 km: enough to find stores, without sharing an exact position.
            JSONObject().put("lat", location.latitude.round2()).put("lng", location.longitude.round2()),
        )
        val stores = response.getJSONArray("stores")
        return (0 until stores.length()).map {
            val s = stores.getJSONObject(it)
            NearbyStore(s.getString("name"), s.getInt("distanceMeters"))
        }
    }

    private fun Double.round2() = (this * 100).roundToLong() / 100.0
}
