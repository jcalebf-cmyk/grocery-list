package com.grocerylist.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.grocerylist.app.data.StoreBrands
import kotlin.math.absoluteValue

/**
 * The chain's logo on a white tile, or a colored tile with the store's initials if there's no logo.
 * Size it with [modifier]; [padding] and [initialsSize] scale the contents to match.
 */
@Composable
fun StoreLogo(
    name: String,
    modifier: Modifier,
    padding: Dp = 6.dp,
    cornerRadius: Dp = 12.dp,
    initialsSize: TextUnit = 20.sp,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val logo = StoreBrands.logoUrl(name)
    if (logo == null) {
        MonogramTile(name, modifier, shape, initialsSize)
        return
    }
    Surface(
        modifier = modifier,
        shape = shape,
        color = Color.White, // logos are drawn for white backgrounds, even in dark mode
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        SubcomposeAsyncImage(
            model = logo,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.padding(padding),
            loading = { Box(Modifier.fillMaxSize()) },
            error = { MonogramTile(name, Modifier.fillMaxSize(), shape, initialsSize) },
        )
    }
}

@Composable
private fun MonogramTile(name: String, modifier: Modifier, shape: RoundedCornerShape, initialsSize: TextUnit) {
    val color = MONOGRAM_COLORS[name.lowercase().hashCode().absoluteValue % MONOGRAM_COLORS.size]
    val initials = name.split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }
    Surface(modifier = modifier, shape = shape, color = color) {
        Box(contentAlignment = Alignment.Center) {
            Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = initialsSize)
        }
    }
}

// Saturated enough for white text, distinct from each other.
private val MONOGRAM_COLORS = listOf(
    Color(0xFF2E7D32), Color(0xFF1565C0), Color(0xFFC62828), Color(0xFF6A1B9A),
    Color(0xFFEF6C00), Color(0xFF00838F), Color(0xFF4E342E), Color(0xFFAD1457),
)
