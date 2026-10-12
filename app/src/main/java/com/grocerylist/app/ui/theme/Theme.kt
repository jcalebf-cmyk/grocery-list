package com.grocerylist.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grocerylist.app.R

// A fixed produce palette (garden green, citrus, warm off-white) instead of wallpaper-based
// dynamic color, so the app looks like itself on every phone.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6B3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9F0D3),
    onPrimaryContainer = Color(0xFF002110),
    secondary = Color(0xFF4F6354),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2E8D5),
    onSecondaryContainer = Color(0xFF0D1F13),
    tertiary = Color(0xFF8A5100),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDAF),
    onTertiaryContainer = Color(0xFF2C1700),
    background = Color(0xFFFBF8F2),
    onBackground = Color(0xFF1C1B17),
    surface = Color(0xFFFBF8F2),
    onSurface = Color(0xFF1C1B17),
    surfaceVariant = Color(0xFFE9E4DA),
    onSurfaceVariant = Color(0xFF4A473F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F2EA),
    surfaceContainer = Color(0xFFF1ECE3),
    surfaceContainerHigh = Color(0xFFEBE6DC),
    surfaceContainerHighest = Color(0xFFE5E0D6),
    outline = Color(0xFF7A766C),
    outlineVariant = Color(0xFFCBC6BB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD8A5),
    onPrimary = Color(0xFF00391C),
    primaryContainer = Color(0xFF0C5128),
    onPrimaryContainer = Color(0xFFABF5C0),
    secondary = Color(0xFFB6CCB9),
    onSecondary = Color(0xFF213528),
    secondaryContainer = Color(0xFF374B3D),
    onSecondaryContainer = Color(0xFFD2E8D5),
    tertiary = Color(0xFFFFB955),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF693C00),
    onTertiaryContainer = Color(0xFFFFDDAF),
    background = Color(0xFF14130F),
    onBackground = Color(0xFFE8E2D8),
    surface = Color(0xFF14130F),
    onSurface = Color(0xFFE8E2D8),
    surfaceVariant = Color(0xFF4A473F),
    onSurfaceVariant = Color(0xFFCCC6BA),
    surfaceContainerLowest = Color(0xFF0F0E0B),
    surfaceContainerLow = Color(0xFF1C1B17),
    surfaceContainer = Color(0xFF211F1B),
    surfaceContainerHigh = Color(0xFF2B2A25),
    surfaceContainerHighest = Color(0xFF36342F),
    outline = Color(0xFF969083),
    outlineVariant = Color(0xFF4A473F),
)

private fun jakarta(weight: FontWeight) = Font(
    R.font.plus_jakarta_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private val Jakarta = FontFamily(
    jakarta(FontWeight.Normal),
    jakarta(FontWeight.Medium),
    jakarta(FontWeight.SemiBold),
    jakarta(FontWeight.Bold),
    jakarta(FontWeight.ExtraBold),
)

private val AppTypography = Typography().run {
    fun TextStyle.jakarta(weight: FontWeight? = null) = copy(fontFamily = Jakarta, fontWeight = weight ?: fontWeight)
    copy(
        displayLarge = displayLarge.jakarta(FontWeight.Bold),
        displayMedium = displayMedium.jakarta(FontWeight.Bold),
        displaySmall = displaySmall.jakarta(FontWeight.Bold),
        headlineLarge = headlineLarge.jakarta(FontWeight.Bold),
        headlineMedium = headlineMedium.jakarta(FontWeight.Bold),
        headlineSmall = headlineSmall.jakarta(FontWeight.Bold),
        titleLarge = titleLarge.jakarta(FontWeight.Bold),
        titleMedium = titleMedium.jakarta(FontWeight.SemiBold),
        titleSmall = titleSmall.jakarta(FontWeight.SemiBold),
        bodyLarge = bodyLarge.jakarta(),
        bodyMedium = bodyMedium.jakarta(),
        bodySmall = bodySmall.jakarta(),
        labelLarge = labelLarge.jakarta(FontWeight.SemiBold),
        labelMedium = labelMedium.jakarta(FontWeight.SemiBold),
        labelSmall = labelSmall.jakarta(FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GroceryListTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        motionScheme = MotionScheme.expressive(),
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
