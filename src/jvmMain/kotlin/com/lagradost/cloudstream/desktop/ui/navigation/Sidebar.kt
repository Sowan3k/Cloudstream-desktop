package com.lagradost.cloudstream.desktop.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream.desktop.ui.theme.*

@Composable
fun DesktopSidebar(
    currentScreen: DesktopScreen,
    onNavigate: (DesktopScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val sidebarBorder = SidebarBorder
    val navItems = listOf(
        NavItem(Icons.Default.Home, "Home", DesktopScreen.Home),
        NavItem(Icons.Default.Search, "Search", DesktopScreen.Search()),
        NavItem(Icons.Default.VideoLibrary, "Library", DesktopScreen.Library),
        NavItem(Icons.Default.Extension, "Plugins", DesktopScreen.Plugins),
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(68.dp)
            .background(SidebarBackground)
            .drawBehind {
                // Right border separator
                drawRect(
                    color = sidebarBorder,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width - 1.dp.toPx(), 0f),
                    size = androidx.compose.ui.geometry.Size(1.dp.toPx(), size.height)
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))

        // Top Navigation Items
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            navItems.forEach { item ->
                val isSelected = when (item.screen) {
                    is DesktopScreen.Home -> currentScreen is DesktopScreen.Home
                    is DesktopScreen.Search -> currentScreen is DesktopScreen.Search
                    is DesktopScreen.Library -> currentScreen is DesktopScreen.Library
                    is DesktopScreen.Plugins -> currentScreen is DesktopScreen.Plugins
                    else -> false
                }

                Win11NavItem(
                    item = item,
                    isSelected = isSelected,
                    onClick = { onNavigate(item.screen) }
                )
            }
        }

        // Bottom Settings Item
        Win11NavItem(
            item = NavItem(Icons.Default.Settings, "Settings", DesktopScreen.Settings),
            isSelected = currentScreen is DesktopScreen.Settings,
            onClick = { onNavigate(DesktopScreen.Settings) }
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Win11NavItem(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val isHighlighted = hovered || focused

    val iconColor by animateColorAsState(
        SidebarForeground.copy(alpha = if (isSelected) 1f else if (isHighlighted) 0.85f else 0.65f),
        animationSpec = tween(150)
    )
    val bgColor by animateColorAsState(
        if (isSelected) SidebarAccent.copy(alpha = 0.12f) else if (isHighlighted) SidebarHover else Color.Transparent,
        animationSpec = tween(150)
    )
    val indicatorColor by animateColorAsState(
        if (isSelected) SidebarAccent else if (focused) SidebarAccent.copy(alpha = 0.6f) else Color.Transparent,
        animationSpec = tween(200)
    )

    Box(
        modifier = Modifier
            .width(56.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(
                width = if (focused) 1.5.dp else 0.dp,
                color = if (focused) SidebarAccent else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .hoverable(interactionSource)
            .focusable(interactionSource = interactionSource)
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Active Indicator Bar on the left
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(3.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(indicatorColor)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                item.icon,
                contentDescription = item.label,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.height(3.dp))
            Text(
                item.label,
                fontSize = 10.sp,
                color = iconColor,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

private data class NavItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val screen: DesktopScreen
)
