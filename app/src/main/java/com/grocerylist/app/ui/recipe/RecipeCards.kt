package com.grocerylist.app.ui.recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.grocerylist.app.data.Recipe
import com.grocerylist.app.data.RecipeMatch
import com.grocerylist.app.data.RecipeSource
import coil3.compose.AsyncImage
import com.grocerylist.app.data.RecipeTint
import com.grocerylist.app.data.recipeEmoji
import com.grocerylist.app.data.videoThumbnail
import com.grocerylist.app.ui.AppIcons

private val Margin = 16.dp
private val Gap = 12.dp
private val WideCard = 180.dp

/**
 * How many recipe cards fit across [width]: two on any phone (one when the text is set very large), more where there's
 * room for cards at least 180dp wide. Same as `RecipeGrid` on iPhone.
 */
@Composable
fun recipeColumns(width: Dp): Int =
    if (LocalDensity.current.fontScale >= 1.5f) 1
    else ((width - Margin * 2 + Gap) / (WideCard + Gap)).toInt().coerceAtLeast(2)

/** Recipe cards in rows of [columns], as part of a scrolling column. */
fun LazyListScope.recipeRows(matches: List<RecipeMatch>, columns: Int, onOpen: (String) -> Unit) {
    items(matches.chunked(columns), key = { it.first().recipe.id }, contentType = { "recipes" }) { row ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Margin, vertical = Gap / 2),
            horizontalArrangement = Arrangement.spacedBy(Gap),
        ) {
            row.forEach { match -> RecipeCard(match, onClick = { onOpen(match.recipe.id) }, Modifier.weight(1f)) }
            // Keeps a last, short row's cards the same width as the rest.
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/**
 * A saved recipe: its picture, its title and the basics, or the ingredient a search matched, with an icon for where it
 * came from.
 */
@Composable
private fun RecipeCard(match: RecipeMatch, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val recipe = match.recipe
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier,
    ) {
        RecipePicture(recipe, Modifier.fillMaxWidth().height(96.dp))
        Column(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Always room for two lines, so every card is the same height whatever its title or the text size.
            Text(
                recipe.title,
                style = MaterialTheme.typography.titleSmall,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    when {
                        recipe.sourceUrl.isNotBlank() -> Icons.Default.PlayArrow
                        recipe.source == RecipeSource.AI -> AppIcons.Sparkle
                        else -> AppIcons.Restaurant
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    match.ingredient?.let { "Has $it" } ?: recipeCardSummary(recipe),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The video's thumbnail, or a food emoji on a colored tile when there's no video (or no connection). */
@Composable
private fun RecipePicture(recipe: Recipe, modifier: Modifier = Modifier) {
    val visual = remember(recipe.title) { recipeEmoji(recipe.title) }
    val thumbnail = remember(recipe.sourceUrl) { videoThumbnail(recipe.sourceUrl) }
    var failed by remember(thumbnail) { mutableStateOf(false) }
    Box(modifier.background(tintColor(visual.tint, isSystemInDarkTheme())), contentAlignment = Alignment.Center) {
        if (thumbnail != null && !failed) {
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // A picture, so it doesn't grow with the text size, and screen readers skip it.
            Text(visual.emoji, fontSize = with(LocalDensity.current) { 44.dp.toSp() }, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

/** Same shades as `RecipeTint.color` in RecipeCards.swift. */
private fun tintColor(tint: RecipeTint, dark: Boolean) = Color(
    when (tint) {
        RecipeTint.RED -> if (dark) 0xFF5C2B25 else 0xFFFFDAD4
        RecipeTint.ORANGE -> if (dark) 0xFF5C3A17 else 0xFFFFDFC2
        RecipeTint.YELLOW -> if (dark) 0xFF4E4313 else 0xFFFBEBB5
        RecipeTint.GREEN -> if (dark) 0xFF23472F else 0xFFD2EBD5
        RecipeTint.PINK -> if (dark) 0xFF552A3B else 0xFFFADAE4
        RecipeTint.BLUE -> if (dark) 0xFF1F4357 else 0xFFD3E6F2
        RecipeTint.TAN -> if (dark) 0xFF4A3F30 else 0xFFEADFCF
    },
)

/** "45 min · Serves 4": as much of [recipeSummary] as fits on a card, or how many ingredients if it says neither. */
fun recipeCardSummary(recipe: Recipe): String = recipeSummary(
    if (recipe.totalMinutes > 0 || recipe.servings > 0) recipe.copy(ingredients = emptyList()) else recipe,
)

/** Looks like a search field; opens the recipes screen ready to type in. */
@Composable
fun RecipeSearchBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Search, contentDescription = null)
            Text("Search recipes", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
