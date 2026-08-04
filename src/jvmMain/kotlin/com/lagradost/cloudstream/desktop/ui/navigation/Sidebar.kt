package com.lagradost.cloudstream.desktop.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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

// ─── Windows 11 Color Tokens ──────────────────────────────────────────────────
private val SurfaceBase    = Color(0xFF1C1C1C)
private val SurfaceLayer   = Color(0xFF242424)
private val SurfaceCard    = Color(0xFF2C2C2C)
private val SurfaceHover   = Color(0xFF323232)
private val BorderSubtle   = Color(0x18FFFFFF)
private val BorderDefault  = Color(0x26FFFFFF)
private val TextPrimary    = Color(0xFFFFFFFF)
private val TextSecondary  = Color(0x99FFFFFF)
private val TextTertiary   = Color(0x66FFFFFF)
private val AccentBlue     = Color(0xFF0078D4)

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

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(68.dp)
            .background(SurfaceBase)
            .drawBehind {
                // Right border separator
                drawRect(
                    color = BorderSubtle,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width - 1.dp.toPx(), 0f),
                    size = androidx.compose.ui.geometry.Size(1.dp.toPx(), size.height)
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        // App Logo
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource("icon.png"),
                    contentDescription = "CloudStream Logo",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

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

    val iconColor by animateColorAsState(
        if (isSelected) TextPrimary else if (hovered) TextSecondary else TextTertiary,
        animationSpec = tween(150)
    )
    val bgColor by animateColorAsState(
        if (isSelected) AccentBlue.copy(alpha = 0.12f) else if (hovered) SurfaceHover else Color.Transparent,
        animationSpec = tween(150)
    )
    val indicatorColor by animateColorAsState(
        if (isSelected) AccentBlue else Color.Transparent,
        animationSpec = tween(200)
    )

    Box(
        modifier = Modifier
            .width(56.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .hoverable(interactionSource)
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
