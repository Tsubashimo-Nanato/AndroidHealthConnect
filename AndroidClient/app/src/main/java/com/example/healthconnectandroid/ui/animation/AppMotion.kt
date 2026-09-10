package com.example.healthconnectandroid.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.navigation.AppDestination
import com.example.healthconnectandroid.navigation.AppNavigationDirection
import com.example.healthconnectandroid.navigation.AppNavigationMotionPolicy

private val StudioEase = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

@Composable
fun Modifier.rowFadeIn(
    index: Int,
    enabled: Boolean = true
): Modifier {
    // One entrance cue is enough; staggering every row creates a coroutine for each visible item.
    if (!enabled || index != 0) return this
    val density = LocalDensity.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(180, easing = StudioEase))
    }
    val offsetPx = with(density) { 6.dp.toPx() }
    return graphicsLayer {
        compositingStrategy = CompositingStrategy.ModulateAlpha
        alpha = progress.value
        translationY = (1f - progress.value) * offsetPx
    }
}

@Composable
fun Modifier.destinationEnterMotion(destination: AppDestination): Modifier {
    val density = LocalDensity.current
    val progress = remember { Animatable(1f) }
    var previousDestination by remember { mutableStateOf(destination) }
    var direction by remember { mutableStateOf(AppNavigationDirection.NONE) }

    LaunchedEffect(destination) {
        direction = AppNavigationMotionPolicy.direction(previousDestination, destination)
        previousDestination = destination
        if (direction == AppNavigationDirection.NONE) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }

        progress.snapTo(0f)
        progress.animateTo(1f, tween(180, easing = StudioEase))
    }

    val offsetPx = with(density) { 10.dp.toPx() }
    return graphicsLayer {
        val remaining = 1f - progress.value
        compositingStrategy = CompositingStrategy.ModulateAlpha
        alpha = 0.92f + progress.value * 0.08f
        translationX = direction.multiplier * remaining * offsetPx
    }
}
