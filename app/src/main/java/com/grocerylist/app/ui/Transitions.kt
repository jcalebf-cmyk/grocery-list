package com.grocerylist.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.RemeasureToBounds
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.navigation.NavBackStackEntry

/** Set by the navigation host so screens can share elements across navigation. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Animates this element between screens that use the same [key]. No-op outside the navigation host. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElement(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@sharedElement.sharedElement(rememberSharedContentState(key), animatedVisibilityScope = animated)
    }
}

/**
 * A container transform: a card with this [key] grows into the screen with the same key as it opens, and shrinks back
 * into it on Back, rounded like the card ([shape]) on the way. Without a matching card (opened from somewhere else),
 * the screen just slides in. iOS does the same with `.navigationTransition(.zoom)`.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedContainer(key: String, shape: Shape): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@sharedContainer.sharedBounds(
            rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            enter = fadeIn(tween(250, delayMillis = 60)),
            exit = fadeOut(tween(120)),
            resizeMode = RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}

// Material's shared X axis: the new screen slides in a little from the side as the old one slides away and fades.
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val SLIDE_MS = 350

typealias NavTransitionScope = AnimatedContentTransitionScope<NavBackStackEntry>

val navEnter: NavTransitionScope.() -> EnterTransition = {
    slideInHorizontally(tween(SLIDE_MS, easing = Emphasized)) { it / 6 } + fadeIn(tween(200, delayMillis = 80))
}
val navExit: NavTransitionScope.() -> ExitTransition = {
    slideOutHorizontally(tween(SLIDE_MS, easing = Emphasized)) { -it / 6 } + fadeOut(tween(120))
}
val navPopEnter: NavTransitionScope.() -> EnterTransition = {
    slideInHorizontally(tween(SLIDE_MS, easing = Emphasized)) { -it / 6 } + fadeIn(tween(200, delayMillis = 80))
}
val navPopExit: NavTransitionScope.() -> ExitTransition = {
    slideOutHorizontally(tween(SLIDE_MS, easing = Emphasized)) { it / 6 } + fadeOut(tween(120))
}
