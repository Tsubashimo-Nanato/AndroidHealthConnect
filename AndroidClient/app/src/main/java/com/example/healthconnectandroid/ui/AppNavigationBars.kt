package com.example.healthconnectandroid.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.navigation.AppDestination
import com.example.healthconnectandroid.navigation.AppNavigationState
import com.example.healthconnectandroid.navigation.AppTab
import com.example.healthconnectandroid.ui.i18n.uiText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppTopBar(
    nav: AppNavigationState,
    onBack: () -> Unit
) {
    TopAppBar(
        title = {
            Text(uiText(nav.title()), fontWeight = FontWeight.SemiBold)
        },
        navigationIcon = {
            if (nav.destination != AppDestination.Dashboard) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 10.dp
    ) {
        NavigationBarItem(
            selected = selectedTab == AppTab.Dashboard,
            onClick = { onSelectTab(AppTab.Dashboard) },
            icon = {
                AnimatedNavIcon(selected = selectedTab == AppTab.Dashboard) {
                    Icon(Icons.Default.Home, contentDescription = null)
                }
            },
            label = { Text(uiText(AppTab.Dashboard.label)) },
            colors = studioNavigationItemColors()
        )
        NavigationBarItem(
            selected = selectedTab == AppTab.Medicine,
            onClick = { onSelectTab(AppTab.Medicine) },
            icon = {
                AnimatedNavIcon(selected = selectedTab == AppTab.Medicine) {
                    Icon(Icons.Default.Notifications, contentDescription = null)
                }
            },
            label = { Text(uiText(AppTab.Medicine.label)) },
            colors = studioNavigationItemColors()
        )
        NavigationBarItem(
            selected = selectedTab == AppTab.Data,
            onClick = { onSelectTab(AppTab.Data) },
            icon = {
                AnimatedNavIcon(selected = selectedTab == AppTab.Data) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                }
            },
            label = { Text(uiText(AppTab.Data.label)) },
            colors = studioNavigationItemColors()
        )
        NavigationBarItem(
            selected = selectedTab == AppTab.Settings,
            onClick = { onSelectTab(AppTab.Settings) },
            icon = {
                AnimatedNavIcon(selected = selectedTab == AppTab.Settings) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                }
            },
            label = { Text(uiText(AppTab.Settings.label)) },
            colors = studioNavigationItemColors()
        )
    }
}

@Composable
private fun studioNavigationItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun AnimatedNavIcon(
    selected: Boolean,
    content: @Composable () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = tween(180),
        label = "bottom-nav-icon-scale"
    )
    Row(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
        content()
    }
}
