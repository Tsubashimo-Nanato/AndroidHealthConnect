package com.example.healthconnectandroid.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.navigation.AppNavigationState
import com.example.healthconnectandroid.navigation.AppTab
import com.example.healthconnectandroid.ui.i18n.uiText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppTopBar(
    nav: AppNavigationState,
    profileName: String,
    onBack: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(uiText(nav.title()), fontWeight = FontWeight.SemiBold)
                Text(
                    profileName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        navigationIcon = {
            if (nav.canNavigateBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = uiText("Back"))
                }
            }
        },
        actions = {},
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
internal fun BottomNavigationBar(
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 0.dp
    ) {
        PrimaryNavigationItems.forEach { item ->
            val selected = selectedTab == item.tab
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (selected) return@NavigationBarItem
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectTab(item.tab)
                },
                icon = {
                    BouncingNavigationIcon(icon = item.icon, selected = selected)
                },
                label = { Text(uiText(item.tab.label)) },
                colors = studioNavigationItemColors()
            )
        }
    }
}

@Composable
private fun BouncingNavigationIcon(
    icon: ImageVector,
    selected: Boolean
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.94f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "navigation-icon-scale"
    )
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    )
}

@Composable
private fun studioNavigationItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
)

private data class PrimaryNavigationItem(
    val tab: AppTab,
    val icon: ImageVector
)

private val PrimaryNavigationItems = listOf(
    PrimaryNavigationItem(AppTab.Dashboard, Icons.Default.Home),
    PrimaryNavigationItem(AppTab.Data, Icons.AutoMirrored.Filled.List),
    PrimaryNavigationItem(AppTab.Medicine, Icons.Default.Medication),
    PrimaryNavigationItem(AppTab.Settings, Icons.Default.Settings)
)
