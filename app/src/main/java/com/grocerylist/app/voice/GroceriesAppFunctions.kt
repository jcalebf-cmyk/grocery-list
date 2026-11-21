package com.grocerylist.app.voice

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionPermissionRequiredException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import com.grocerylist.app.MainActivity
import com.grocerylist.app.data.VoiceText
import kotlinx.coroutines.CancellationException

/** A recipe from the household's recipe box. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class RecipeDetails(
    /** What to tell the user. */
    val message: String,
    /** The recipe's title. */
    val title: String,
    /** Ingredients, each with its amount, e.g. "2 cloves garlic". */
    val ingredients: List<String>,
    /** The steps, in order. */
    val steps: List<String>,
    /** Opens the recipe in Groceries. */
    val intentToOpen: PendingIntent,
)

/**
 * Lets Gemini (and other assistants on Android 16+) work with the household's grocery lists and recipe box.
 * The AppFunctions compiler generates `GroceriesAppFunctionService` from this class.
 */
@RequiresApi(36)
@AppFunctionServiceEntryPoint(
    serviceName = "GroceriesAppFunctionService",
    appFunctionXmlFileName = "groceries_app_function_service",
)
abstract class BaseGroceriesAppFunctionService : AppFunctionService() {
    private val runner = VoiceActionRunner()

    /**
     * Adds items to one of the household's shopping lists, shared with everyone in the household.
     * Use this when the user wants something added to their grocery or shopping list.
     * @param items What to add. Separate several items with commas, e.g. "milk, eggs, mac and cheese".
     * @param store The store whose list it goes on, e.g. "Costco", or "shopping" for the list that isn't for a
     *   store. Leave empty if the user didn't say: the only open list is used, or a new shopping list is started.
     *   A store the household hasn't used yet is added.
     * @return A sentence to tell the user, saying what was added and to which list.
     * @throws AppFunctionInvalidArgumentException If no items were given, or no store was given and several
     *   lists are open. The message says what to ask the user; call again with their answer.
     * @throws AppFunctionPermissionRequiredException If the user isn't signed in to Groceries or has no household.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun addToList(items: String, store: String?): String = voice { runner.addItems(items, store) }

    /**
     * Reads out what's still to get on one of the household's shopping lists (items not checked off yet).
     * @param store The store whose list to read, e.g. "Trader Joe's", or "shopping" for the list that isn't for a
     *   store. Leave empty if the user didn't say.
     * @return A sentence listing the items, to tell the user.
     * @throws AppFunctionInvalidArgumentException If no store was given and several lists are open. Ask which.
     * @throws AppFunctionElementNotFoundException If there's no open list for that store, or no open lists.
     * @throws AppFunctionPermissionRequiredException If the user isn't signed in to Groceries or has no household.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun readList(store: String?): String = voice { runner.readList(store) }

    /**
     * Starts a shopping list for a store. If that store already has an open list, it's kept rather than
     * starting a second one.
     * @param store The store, e.g. "Costco", or "shopping" for a list that isn't for a store. A store the
     *   household hasn't used yet is added.
     * @return A sentence to tell the user.
     * @throws AppFunctionPermissionRequiredException If the user isn't signed in to Groceries or has no household.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun startList(store: String): String = voice { runner.startList(store) }

    /**
     * Checks an item off one of the household's shopping lists, as if it was put in the cart.
     * @param item The item to check off, e.g. "milk".
     * @param store The store whose list it's on. Leave empty if the user didn't say.
     * @return A sentence to tell the user.
     * @throws AppFunctionInvalidArgumentException If no store was given and several lists are open. Ask which.
     * @throws AppFunctionElementNotFoundException If the item isn't on the list, or there's no such open list.
     * @throws AppFunctionPermissionRequiredException If the user isn't signed in to Groceries or has no household.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun checkOff(item: String, store: String?): String = voice { runner.checkOff(item, store) }

    /**
     * Finds a recipe in the household's recipe box by name, to show or open it.
     * @param name Words from the recipe's title, e.g. "garlic pasta".
     * @return The recipe, with an intent that opens it in Groceries. Open it when the user asked to open or
     *   show the recipe.
     * @throws AppFunctionElementNotFoundException If no saved recipe matches.
     * @throws AppFunctionPermissionRequiredException If the user isn't signed in to Groceries or has no household.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun openRecipe(name: String): RecipeDetails = voice {
        val recipe = runner.findRecipe(name)
        val open = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_RECIPE)
            .putExtra(MainActivity.EXTRA_RECIPE_ID, recipe.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        RecipeDetails(
            message = VoiceText.openingRecipe(recipe.title),
            title = recipe.title,
            ingredients = recipe.ingredients.map { listOf(it.quantity, it.name).filter(String::isNotBlank).joinToString(" ") },
            steps = recipe.steps,
            intentToOpen = PendingIntent.getActivity(
                this, recipe.id.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
    }

    /** Turns a [VoiceProblem] into the AppFunctions error an assistant understands, keeping its wording. */
    private inline fun <T> voice(action: () -> T): T = try {
        action()
    } catch (e: CancellationException) {
        throw e
    } catch (e: VoiceProblem) {
        throw when (e) {
            is VoiceProblem.SignedOut, is VoiceProblem.NoHousehold -> AppFunctionPermissionRequiredException(e.message)
            is VoiceProblem.NeedsInput -> AppFunctionInvalidArgumentException(e.message)
            is VoiceProblem.NotFound -> AppFunctionElementNotFoundException(e.message)
        }
    } catch (e: Exception) {
        throw AppFunctionAppUnknownException("Groceries couldn't do that right now. Try again in a moment.")
    }
}
