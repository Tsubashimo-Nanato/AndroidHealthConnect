package com.example.healthconnectandroid.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val StudioEase = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

@Composable
fun Modifier.rowFadeIn(
    index: Int,
    enabled: Boolean = true,
    maxAnimatedIndex: Int = 16
): Modifier {
    if (!enabled || index > maxAnimatedIndex) return this
    val density = LocalDensity.current
    val alpha = remember(index) { Animatable(0f) }
    val offset = remember(index) { Animatable(1f) }
    LaunchedEffect(index) {
        delay((index * 32L).coerceAtMost(220L))
        coroutineScope {
            launch { alpha.animateTo(1f, tween(260, easing = StudioEase)) }
            launch { offset.animateTo(0f, tween(260, easing = StudioEase)) }
        }
    }
    val offsetPx = with(density) { 10.dp.toPx() }
    return graphicsLayer {
        this.alpha = alpha.value
        translationY = offset.value * offsetPx
    }
}
