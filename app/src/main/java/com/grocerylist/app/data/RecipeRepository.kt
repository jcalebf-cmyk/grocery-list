package com.grocerylist.app.data

import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

data class Ingredient(val name: String, val quantity: String)

/**
 * Where a saved recipe came from: written by AI (in the chat, or for a YouTube video that didn't come with
 * the recipe), pasted text, or copied from a YouTube video's description or the creator's comment.
 */
enum class RecipeSource(val key: String) {
    AI("ai"),
    PASTED("pasted"),
    YOUTUBE("youtube");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: PASTED
    }
}

/**
 * A recipe in the household's recipe box, or one the AI chat just suggested ([id] empty until saved).
 * [servings] and [totalMinutes] are 0 when the source didn't say. [originalText] keeps a pasted recipe's
 * text as it was, in case the import got something wrong. A recipe from a YouTube link has the video in
 * [sourceUrl] and its channel in [sourceName]. Same document as iOS's `Recipe`.
 */
data class Recipe(
    val id: String = "",
    val title: String,
    val description: String = "",
    val servings: Int = 0,
    val totalMinutes: Int = 0,
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<String> = emptyList(),
    val notes: String = "",
    val source: RecipeSource = RecipeSource.PASTED,
    val originalText: String = "",
    val sourceUrl: String = "",
    val sourceName: String = "",
    val createdBy: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** One message in the recipe chat. Assistant messages can carry recipe cards. */
data class ChatMessage(val fromUser: Boolean, val text: String, val recipes: List<Recipe> = emptyList())

/**
 * The household's saved recipes (`households/{hid}/savedRecipes`), plus the server calls that make them:
 * the AI chat and reading a pasted recipe. Chats stay on the phone (RecipeChats); only recipes someone saves are shared.
 */
class RecipeRepository(householdId: String) {
    private val recipes = Firebase.firestore.collection("households").document(householdId).collection("savedRecipes")

    /** Newest first. */
    fun observeAll(): Flow<List<Recipe>> = recipes.snapshots().map { snap ->
        snap.documents.mapNotNull(::parse).sortedByDescending { it.createdAt }
    }

    /** A one-time read, for voice actions. */
    suspend fun all(): List<Recipe> = recipes.get().await().documents.mapNotNull(::parse)

    fun observe(id: String): Flow<Recipe?> = recipes.document(id).snapshots().map(::parse)

    /** Saves a new recipe and returns its ID. Works offline: Firestore syncs it later. */
    fun add(recipe: Recipe, uid: String): String {
        val doc = recipes.document()
        val now = System.currentTimeMillis()
        doc.set(toFirestore(recipe) + mapOf("createdBy" to uid, "createdAt" to now, "updatedAt" to now))
        return doc.id
    }

    fun update(recipe: Recipe) {
        recipes.document(recipe.id).update(toFirestore(recipe) + mapOf("updatedAt" to System.currentTimeMillis()))
    }

    fun delete(id: String) {
        recipes.document(id).delete()
    }

    /** Sends the conversation (ending with the user's new message) and returns the AI's reply. */
    suspend fun chat(messages: List<ChatMessage>): ChatMessage {
        val turns = JSONArray(
            messages.takeLast(MAX_TURNS).dropWhile { !it.fromUser }.map { m ->
                JSONObject()
                    .put("role", if (m.fromUser) "user" else "assistant")
                    .put("text", m.text)
                    .apply { if (!m.fromUser) put("recipes", JSONArray(m.recipes.map(::recipeCardJson))) }
            },
        )
        val reply = ServerApi.post(
            "/api/recipe-chat",
            JSONObject().put("messages", turns),
            readTimeoutMs = 60_000,
        )
        val cards = reply.optJSONArray("recipes") ?: JSONArray()
        return ChatMessage(
            fromUser = false,
            text = reply.optString("reply"),
            recipes = (0 until cards.length()).map { recipeFromCardJson(cards.getJSONObject(it), RecipeSource.AI) },
        )
    }

    /**
     * Reads a pasted recipe, or the recipe from a pasted YouTube link, into a card. Pasted text keeps the
     * author's wording. Throws [ServerException] (422 if there's no recipe in it).
     */
    suspend fun import(text: String): Recipe {
        val reply = ServerApi.post("/api/recipe-import", JSONObject().put("text", text), readTimeoutMs = 60_000)
        val source = RecipeSource.of(reply.optString("source"))
        val recipe = recipeFromCardJson(reply.getJSONObject("recipe"), source)
        val video = reply.optJSONObject("video")
            ?: return recipe.copy(originalText = text)
        return recipe.copy(sourceUrl = video.optString("url"), sourceName = video.optString("channel"))
    }

    private fun toFirestore(r: Recipe): Map<String, Any> = mapOf(
        "title" to r.title,
        "description" to r.description,
        "servings" to r.servings,
        "totalMinutes" to r.totalMinutes,
        "ingredients" to r.ingredients.map { mapOf("name" to it.name, "quantity" to it.quantity) },
        "steps" to r.steps,
        "notes" to r.notes,
        "source" to r.source.key,
        "originalText" to r.originalText,
        "sourceUrl" to r.sourceUrl,
        "sourceName" to r.sourceName,
    )

    @Suppress("UNCHECKED_CAST")
    private fun parse(snap: DocumentSnapshot): Recipe? {
        if (!snap.exists()) return null
        return Recipe(
            id = snap.id,
            title = snap.getString("title").orEmpty(),
            description = snap.getString("description").orEmpty(),
            servings = snap.getLong("servings")?.toInt() ?: 0,
            totalMinutes = snap.getLong("totalMinutes")?.toInt() ?: 0,
            ingredients = (snap.get("ingredients") as? List<Map<String, Any?>>).orEmpty().map {
                Ingredient(it["name"] as? String ?: "", it["quantity"] as? String ?: "")
            },
            steps = (snap.get("steps") as? List<String>).orEmpty(),
            notes = snap.getString("notes").orEmpty(),
            source = RecipeSource.of(snap.getString("source")),
            originalText = snap.getString("originalText").orEmpty(),
            sourceUrl = snap.getString("sourceUrl").orEmpty(),
            sourceName = snap.getString("sourceName").orEmpty(),
            createdBy = snap.getString("createdBy").orEmpty(),
            createdAt = snap.getLong("createdAt") ?: 0,
            updatedAt = snap.getLong("updatedAt") ?: 0,
        )
    }

    private companion object {
        const val MAX_TURNS = 40 // the server's cap
    }
}

/** The card fields only, as the server sends and takes them. */
internal fun recipeCardJson(r: Recipe) = JSONObject()
    .put("title", r.title)
    .put("description", r.description)
    .put("servings", r.servings)
    .put("totalMinutes", r.totalMinutes)
    .put("ingredients", JSONArray(r.ingredients.map { JSONObject().put("name", it.name).put("quantity", it.quantity) }))
    .put("steps", JSONArray(r.steps))
    .put("notes", r.notes)

internal fun recipeFromCardJson(o: JSONObject, source: RecipeSource): Recipe {
    val ingredients = o.optJSONArray("ingredients") ?: JSONArray()
    val steps = o.optJSONArray("steps") ?: JSONArray()
    return Recipe(
        title = o.optString("title"),
        description = o.optString("description"),
        servings = o.optInt("servings"),
        totalMinutes = o.optInt("totalMinutes"),
        ingredients = (0 until ingredients.length()).map {
            val i = ingredients.getJSONObject(it)
            Ingredient(i.optString("name"), i.optString("quantity"))
        },
        steps = (0 until steps.length()).map { steps.getString(it) },
        notes = o.optString("notes"),
        source = source,
    )
}
