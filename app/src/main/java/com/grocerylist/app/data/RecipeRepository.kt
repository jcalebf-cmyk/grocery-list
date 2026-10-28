package com.grocerylist.app.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

data class Ingredient(val name: String, val quantity: String)

/** Recipes from before 0.10 have no [description], [servings] (0), or [tip]. */
data class Recipe(
    val videoId: String,
    val title: String,
    val description: String,
    val servings: Int,
    val totalMinutes: Int,
    val ingredients: List<Ingredient>,
    val steps: List<String>,
    val tip: String,
    val usesHouseholdItems: List<String>,
    val videoUrl: String,
    val channel: String,
    val thumbnailUrl: String,
)

/**
 * [onHandKeys] and [cuisines] record the kitchen and favorite cuisines these were generated from, so
 * changes trigger new ideas. [mood] is what the household picked for tonight, if anything.
 */
data class RecipeSet(
    val generatedAt: Long,
    val onHandKeys: List<String>,
    val cuisines: List<String>,
    val mood: List<String>,
    val recipes: List<Recipe>,
)

enum class RefreshResult { UPDATED, STILL_FRESH, BUSY, RATE_LIMITED, FAILED }

/**
 * Recipes are written by the Vercel server (Claude, plus a YouTube video of each dish) at most once a day per
 * household, then cached in Firestore so every member sees the same set without paying for it again.
 */
class RecipeRepository(householdId: String) {
    private val recipes = Firebase.firestore.collection("households").document(householdId).collection("recipes")
    private val doc = recipes.document("current")
    private val prefsDoc = recipes.document("preferences")
    private val lock = Mutex()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    fun observe(): Flow<RecipeSet?> = doc.snapshots().map(::parse)

    /**
     * The household's favorite cuisines. A missing document only counts once the server confirms it, so
     * an empty local cache doesn't send people who already chose back through setup.
     */
    @Suppress("UNCHECKED_CAST")
    fun observePreferences(): Flow<CuisinePrefs> = prefsDoc.snapshots(MetadataChanges.INCLUDE)
        .filter { it.exists() || !it.metadata.isFromCache }
        .map { CuisinePrefs((it.get("cuisines") as? List<String>).orEmpty(), chosen = it.exists()) }
        .distinctUntilChanged()

    fun savePreferences(cuisines: List<String>) {
        prefsDoc.set(mapOf("cuisines" to cuisines, "updatedAt" to System.currentTimeMillis()))
    }

    /**
     * Asks the server for new recipes. Without [force], does nothing if today's recipes already exist
     * and were made with the same on-hand ingredients and favorite cuisines.
     * [mood] replaces tonight's cuisines (empty clears them); null keeps the current ones until the daily refresh.
     * [surprise] ignores the kitchen and regular groceries: random dishes from the household's cuisines.
     * With nothing in the kitchen and little history, recipes come from the cuisines alone.
     */
    suspend fun refresh(
        stats: List<ItemStat>,
        onHand: List<OnHandItem>,
        favorites: List<String>,
        mood: List<String>?,
        force: Boolean,
        surprise: Boolean = false,
    ): RefreshResult {
        val (pantry, kitchen) = onHand.partition { it.isPantry }
        val onHandKeys = onHand.mapTo(HashSet()) { it.key }
        val onHandSignature = onHandKeys.sorted()
        val cuisines = favorites.sorted()
        val regulars = suggestItems(stats, storeId = "", alreadyOnList = onHandKeys, now = System.currentTimeMillis(), limit = 15)
            .map { it.displayName }
        if (!lock.tryLock()) return RefreshResult.BUSY
        _refreshing.value = true
        try {
            val current = parse(doc.get().await())
            val today = current?.takeIf { System.currentTimeMillis() - it.generatedAt < REFRESH_INTERVAL_MS }
            val fresh = today != null && today.onHandKeys == onHandSignature && today.cuisines == cuisines
            if (!force && fresh) {
                return RefreshResult.STILL_FRESH
            }
            val tonight = mood ?: today?.mood.orEmpty()
            val recipes = ServerApi.post(
                "/api/recipes",
                JSONObject()
                    .put("items", JSONArray(regulars))
                    .put("onHand", JSONArray(kitchen.map { it.name }.take(30)))
                    .put("pantry", JSONArray(pantry.map { it.name }.take(40)))
                    .put("cuisines", JSONArray(cuisines))
                    .put("mood", JSONArray(tonight))
                    .put("surprise", surprise),
                readTimeoutMs = 120_000, // a Claude call, then a YouTube search per recipe
            ).getJSONArray("recipes")
            // An empty result (e.g. a server hiccup) shouldn't wipe out the current recipes.
            if (recipes.length() == 0 && current != null) return RefreshResult.FAILED
            doc.set(
                mapOf(
                    "generatedAt" to System.currentTimeMillis(),
                    "onHandKeys" to onHandSignature,
                    "cuisines" to cuisines,
                    "mood" to tonight,
                    "recipes" to toFirestore(recipes),
                ),
            ).await()
            return RefreshResult.UPDATED
        } catch (e: ServerException) {
            Log.w(TAG, "Recipe refresh failed", e)
            return if (e.isRateLimited) RefreshResult.RATE_LIMITED else RefreshResult.FAILED
        } catch (e: Exception) {
            Log.w(TAG, "Recipe refresh failed", e)
            return RefreshResult.FAILED
        } finally {
            _refreshing.value = false
            lock.unlock()
        }
    }

    private fun toFirestore(recipes: JSONArray): List<Map<String, Any>> = (0 until recipes.length()).map { i ->
        val r = recipes.getJSONObject(i)
        mapOf(
            "videoId" to r.getString("videoId"),
            "title" to r.getString("title"),
            "description" to r.optString("description"),
            "servings" to r.optInt("servings"),
            "totalMinutes" to r.optInt("totalMinutes"),
            "ingredients" to r.getJSONArray("ingredients").let { arr ->
                (0 until arr.length()).map { j ->
                    val ing = arr.getJSONObject(j)
                    mapOf("name" to ing.getString("name"), "quantity" to ing.optString("quantity"))
                }
            },
            "steps" to r.getJSONArray("steps").strings(),
            "tip" to r.optString("tip"),
            "usesHouseholdItems" to r.optJSONArray("usesHouseholdItems").strings(),
            "videoUrl" to r.getString("videoUrl"),
            "channel" to r.optString("channel"),
            "thumbnailUrl" to r.optString("thumbnailUrl"),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parse(snap: DocumentSnapshot): RecipeSet? {
        if (!snap.exists()) return null
        val raw = snap.get("recipes") as? List<Map<String, Any?>> ?: emptyList()
        return RecipeSet(
            generatedAt = snap.getLong("generatedAt") ?: 0,
            onHandKeys = (snap.get("onHandKeys") as? List<String>).orEmpty(),
            cuisines = (snap.get("cuisines") as? List<String>).orEmpty(),
            mood = (snap.get("mood") as? List<String>).orEmpty(),
            recipes = raw.map { r ->
                Recipe(
                    videoId = r["videoId"] as? String ?: "",
                    title = r["title"] as? String ?: "",
                    description = r["description"] as? String ?: "",
                    servings = (r["servings"] as? Number)?.toInt() ?: 0,
                    totalMinutes = (r["totalMinutes"] as? Number)?.toInt() ?: 0,
                    ingredients = (r["ingredients"] as? List<Map<String, Any?>>).orEmpty().map {
                        Ingredient(it["name"] as? String ?: "", it["quantity"] as? String ?: "")
                    },
                    steps = (r["steps"] as? List<String>).orEmpty(),
                    tip = r["tip"] as? String ?: "",
                    usesHouseholdItems = (r["usesHouseholdItems"] as? List<String>).orEmpty(),
                    videoUrl = r["videoUrl"] as? String ?: "",
                    channel = r["channel"] as? String ?: "",
                    thumbnailUrl = r["thumbnailUrl"] as? String ?: "",
                )
            },
        )
    }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }

    private companion object {
        const val TAG = "RecipeRepository"
        const val REFRESH_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
