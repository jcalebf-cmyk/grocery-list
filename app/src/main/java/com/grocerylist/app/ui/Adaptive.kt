package com.grocerylist.app.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

/*
 * Foldables and tablets. Phones, and a folded Pixel Fold, look the same as always. On a wide window, single-column
 * screens keep a readable width instead of stretching edge to edge, and screens that split naturally (Home, a recipe)
 * show two panes, divided at the fold when the phone is half open like a book.
 * Same breakpoints as `AdaptiveLayout.swift` on iPhone.
 */

/** The widest a column of rows and text gets. */
val ReadableWidth = 640.dp

/** From this width two panes sit side by side (Material's "expanded" window width; an unfolded Pixel Fold is 841dp). */
private val TwoPaneWidth = 840.dp

/** Where a half-open fold crosses the window, left to right, in window coordinates. */
@Immutable
data class Fold(val left: Dp, val right: Dp)

@Immutable
data class WindowLayout(
    val width: Dp,
    /** A fold running top to bottom that the phone is bent at (book posture); null when flat or folded shut. */
    val bookFold: Fold?,
) {
    val twoPane: Boolean get() = bookFold != null || width >= TwoPaneWidth
}

/** The current window's width and fold; updates as the phone is unfolded, bent or rotated. */
@Composable
fun rememberWindowLayout(): WindowLayout {
    val density = LocalDensity.current
    val width = windowWidth()
    val activity = LocalActivity.current
    val fold by produceState<Fold?>(null, activity, density) {
        if (activity == null) return@produceState
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { info ->
            value = info.displayFeatures
                .filterIsInstance<FoldingFeature>()
                .firstOrNull { it.isSeparating && it.orientation == FoldingFeature.Orientation.VERTICAL }
                ?.let { with(density) { Fold(it.bounds.left.toDp(), it.bounds.right.toDp()) } }
        }
    }
    return WindowLayout(width, fold)
}

/** How wide the app's window is. */
@Composable
fun windowWidth(): Dp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }

/**
 * [this] padding plus side gutters that keep a full-width scrolling column's content at most [max] wide and centered.
 * The column itself stays full width, so it still scrolls from anywhere on screen.
 */
@Composable
fun PaddingValues.readable(max: Dp = ReadableWidth): PaddingValues {
    val direction = LocalLayoutDirection.current
    val gutter = ((windowWidth() - max) / 2).coerceAtLeast(0.dp)
    return PaddingValues(
        start = calculateStartPadding(direction) + gutter,
        top = calculateTopPadding(),
        end = calculateEndPadding(direction) + gutter,
        bottom = calculateBottomPadding(),
    )
}

/**
 * Two panes side by side, split at the fold when there is one and in half otherwise, with a hairline between.
 * Only call this when [WindowLayout.twoPane] is true.
 */
@Composable
fun TwoPanes(
    layout: WindowLayout,
    modifier: Modifier = Modifier,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    val fold = layout.bookFold
    if (fold != null) {
        val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
        // The fold's bounds are measured from the left edge; in right-to-left the first pane is on the right.
        val firstWidth = if (ltr) fold.left else layout.width - fold.right
        Row(modifier.fillMaxSize()) {
            Box(Modifier.width(firstWidth).fillMaxHeight()) { first() }
            Spacer(Modifier.width(fold.right - fold.left))
            Box(Modifier.weight(1f).fillMaxHeight()) { second() }
        }
    } else {
        Row(modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) { first() }
            VerticalDivider()
            Box(Modifier.weight(1f).fillMaxHeight()) { second() }
        }
    }
}
