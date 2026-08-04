package com.lagradost.cloudstream.desktop.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource

@Composable
fun DesktopSidebar(
    currentScreen: DesktopScreen,
    onNavigate: (DesktopScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val navItems = listOf(
        NavItem(Icons.Default.Home, "Home", DesktopScreen.Home),
        NavItem(Icons.Default.Search, "Search", DesktopScreen.Search()),
        NavItem(Icons.Default.VideoLibrary, "Library", DesktopScreen.Library),
        NavItem(Icons.Default.Extension, "Plugins", DesktopScreen.Plugins),
    )

    NavigationRail(
        modifier = modifier.fillMaxHeight().width(78.dp),
        containerColor = Color(0xFF111118),
        contentColor = Color.White,
        header = {
            Spacer(Modifier.height(8.dp))
            Image(
                painter = painterResource("icon.png"),
                contentDescription = "CloudStream Logo",
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    ) {
        Spacer(Modifier.height(8.dp))

        navItems.forEach { item ->
            val isSelected = when (item.screen) {
                is DesktopScreen.Home -> currentScreen is DesktopScreen.Home
                is DesktopScreen.Search -> currentScreen is DesktopScreen.Search
                is DesktopScreen.Library -> currentScreen is DesktopScreen.Library
                is DesktopScreen.Plugins -> currentScreen is DesktopScreen.Plugins
                else -> false
            }
            NavigationRailItem(
                selected = isSelected,
                onClick = { onNavigate(item.screen) },
                icon = {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = { Text(item.label, fontSize = 10.sp) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                )
            )
        }

        Spacer(Modifier.weight(1f))

        NavigationRailItem(
            selected = currentScreen is DesktopScreen.Settings,
            onClick = { onNavigate(DesktopScreen.Settings) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(22.dp)) },
            label = { Text("Settings", fontSize = 10.sp) },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray
            )
        )

        Spacer(Modifier.height(16.dp))
    }
}

private data class NavItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val screen: DesktopScreen
)
