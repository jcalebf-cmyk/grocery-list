package com.grocerylist.app.data

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** [lat]/[lng] are missing only in results saved by app versions before 0.9. */
data class NearbyStore(val name: String, val distanceMeters: Int, val lat: Double? = null, val lng: Double? = null)

/**
 * Nearby grocery stores, saved on the phone so the paid Places lookup runs about once a week per
 * area: results are reused until they're 7 days old or the user has moved more than ~1.5 km, with
 * distances re-measured from where the user is now. Getting the phone's location is free; only the
 * server lookup costs money.
 */
object NearbyStores {
    private const val PREFS = "nearby_stores"
    private const val KEY = "last_lookup"

    @Volatile private var memory: SavedLookup? = null

    /** Forgets saved lookups (which reveal roughly where the user shops), e.g. when their account is deleted. */
    fun clear(context: Context) {
        memory = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { clear() }
    }

    /** The last saved results, if fresh, for showing immediately before the location check finishes. */
    fun peek(context: Context): List<NearbyStore>? =
        saved(context)?.takeIf { it.isFresh(System.currentTimeMillis()) }?.stores

    /** Caller must hold ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION. Throws if no location and nothing saved to fall back on. */
    @SuppressLint("MissingPermission")
    suspend fun findCached(context: Context): List<NearbyStore> {
        val previous = saved(context)
        val client = LocationServices.getFusedLocationProviderClient(context)
        val location = runCatching {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await() ?: client.lastLocation.await()
        }.getOrNull()
            ?: return previous?.stores ?: error("Location unavailable")

        val lat = location.latitude
        val lng = location.longitude
        val now = System.currentTimeMillis()
        if (previous != null && previous.canReuseAt(lat, lng, now)) return previous.measuredFrom(lat, lng)

        val response = ServerApi.post("/api/stores", JSONObject().put("lat", lat).put("lng", lng))
        val stores = response.getJSONArray("stores").let { arr ->
            (0 until arr.length()).map { storeFromJson(arr.getJSONObject(it)) }
        }
        save(context, SavedLookup(lat, lng, now, stores))
        return stores
    }

    private fun saved(context: Context): SavedLookup? {
        memory?.let { return it }
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        return runCatching { SavedLookup.fromJson(JSONObject(json)) }.getOrNull()?.also { memory = it }
    }

    private fun save(context: Context, lookup: SavedLookup) {
        memory = lookup
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, lookup.toJson().toString()) }
    }
}

/** One saved Places lookup: where it was made, when, and what it found. */
data class SavedLookup(val lat: Double, val lng: Double, val fetchedAt: Long, val stores: List<NearbyStore>) {
    fun isFresh(now: Long) = now - fetchedAt < MAX_AGE_MS

    /** Results saved before stores carried coordinates can't be re-measured, so they're looked up again. */
    fun canReuseAt(lat: Double, lng: Double, now: Long) =
        isFresh(now) && distanceMeters(this.lat, this.lng, lat, lng) < REUSE_RADIUS_METERS &&
            stores.all { it.lat != null && it.lng != null }

    /** The saved stores with distances from ([lat], [lng]), closest first. */
    fun measuredFrom(lat: Double, lng: Double): List<NearbyStore> = stores
        .map { s -> if (s.lat == null || s.lng == null) s else s.copy(distanceMeters = distanceMeters(lat, lng, s.lat, s.lng).roundToInt()) }
        .sortedBy { it.distanceMeters }

    fun toJson(): JSONObject = JSONObject()
        .put("lat", lat).put("lng", lng).put("fetchedAt", fetchedAt)
        .put("stores", JSONArray(stores.map { s ->
            JSONObject().put("name", s.name).put("distanceMeters", s.distanceMeters)
                .apply { if (s.lat != null && s.lng != null) put("lat", s.lat).put("lng", s.lng) }
        }))

    companion object {
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
        const val REUSE_RADIUS_METERS = 1_500.0

        fun fromJson(json: JSONObject): SavedLookup {
            val arr = json.getJSONArray("stores")
            return SavedLookup(
                lat = json.getDouble("lat"),
                lng = json.getDouble("lng"),
                fetchedAt = json.getLong("fetchedAt"),
                stores = (0 until arr.length()).map { storeFromJson(arr.getJSONObject(it)) },
            )
        }
    }
}

private fun storeFromJson(s: JSONObject) = NearbyStore(
    name = s.getString("name"),
    distanceMeters = s.getInt("distanceMeters"),
    lat = if (s.has("lat")) s.getDouble("lat") else null,
    lng = if (s.has("lng")) s.getDouble("lng") else null,
)

internal fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    fun rad(d: Double) = Math.toRadians(d)
    val a = sin(rad(lat2 - lat1) / 2).pow(2) + cos(rad(lat1)) * cos(rad(lat2)) * sin(rad(lng2 - lng1) / 2).pow(2)
    return 2 * 6_371_000 * asin(sqrt(a))
}
