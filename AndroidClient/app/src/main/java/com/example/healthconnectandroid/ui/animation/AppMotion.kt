package com.example.healthconnectandroid.ui.animation

import androidx.compose.animation.core.Animatable
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
        delay((index * 28L).coerceAtMost(180L))
        coroutineScope {
            launch { alpha.animateTo(1f, tween(170)) }
            launch { offset.animateTo(0f, tween(170)) }
        }
    }
    val offsetPx = with(density) { 8.dp.toPx() }
    return graphicsLayer {
        this.alpha = alpha.value
        translationY = offset.value * offsetPx
    }
}
