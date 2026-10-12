package com.grocerylist.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * Frosted glass for bars and overlays: content behind is blurred and tinted with the surface color.
 * Requires Android 12+ for the blur; older versions fall back to a translucent tint.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun glass(): HazeStyle = HazeMaterials.thin(MaterialTheme.colorScheme.surface)

/** Lighter glass for text laid over photos, so more of the image shows through. */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun photoGlass(): HazeStyle = HazeMaterials.ultraThin(MaterialTheme.colorScheme.surface)
