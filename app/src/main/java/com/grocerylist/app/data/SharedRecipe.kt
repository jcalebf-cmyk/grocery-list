package com.grocerylist.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import java.net.URI
import java.net.URLEncoder

/**
 * Text shared to Groceries from another app (a recipe, or a YouTube link), waiting for the paste screen.
 * Kept in memory until someone signed in to a household picks it up.
 */
object SharedRecipe {
    val pending = MutableStateFlow<String?>(null)

    /** The waiting text, now handled. */
    fun take(): String? = pending.value.also { pending.value = null }
}

/**
 * A recipe share link someone opened (https://www.householdgroceries.app/r/<code>): its code, waiting until
 * someone signed in to a household can see it and save a copy.
 */
object SharedRecipeLink {
    val pending = MutableStateFlow<String?>(null)
}

/** Why a shared recipe couldn't be opened. */
enum class SharedRecipeProblem { EXPIRED, NOT_FOUND, OFFLINE }

class SharedRecipeException(val problem: SharedRecipeProblem) : Exception(problem.name)

/**
 * The code in a recipe share link, or null if [url] isn't one. Same rules as `shareCode(from:)` in Logic.swift
 * and `isShareCode` on the server.
 */
fun shareCodeFrom(url: String?): String? {
    val uri = runCatching { URI(url?.trim() ?: return null) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() != "https") return null
    if (uri.host?.lowercase() !in SHARE_HOSTS) return null
    val code = Regex("^/r/([^/]+)/?$").find(uri.path ?: "")?.groupValues?.get(1) ?: return null
    return code.takeIf { SHARE_CODE.matches(it) }
}

private val SHARE_HOSTS = setOf("www.householdgroceries.app", "householdgroceries.app")
private val SHARE_CODE = Regex("^[23456789abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ]{10}$")

/** The recipe behind a share link, as its sender saw it. Throws [SharedRecipeException]. */
suspend fun fetchSharedRecipe(code: String): Recipe {
    val reply = try {
        ServerApi.get("/api/recipe-share?code=${URLEncoder.encode(code, "UTF-8")}", signedIn = false)
    } catch (e: ServerException) {
        throw SharedRecipeException(
            when (e.status) {
                410 -> SharedRecipeProblem.EXPIRED
                404 -> SharedRecipeProblem.NOT_FOUND
                else -> SharedRecipeProblem.OFFLINE
            },
        )
    } catch (e: java.io.IOException) {
        throw SharedRecipeException(SharedRecipeProblem.OFFLINE)
    }
    val o = reply.getJSONObject("recipe")
    return recipeFromCardJson(o, RecipeSource.of(o.optString("source"))).copy(
        originalText = o.optString("originalText"),
        sourceUrl = o.optString("sourceUrl"),
        sourceName = o.optString("sourceName"),
    )
}
