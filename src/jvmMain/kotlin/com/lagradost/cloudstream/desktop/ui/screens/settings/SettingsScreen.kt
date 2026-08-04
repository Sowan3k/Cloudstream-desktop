package com.lagradost.cloudstream.desktop.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream.desktop.storage.DesktopDataStore

// ─── Windows 11 Fluent Theme Color Tokens ─────────────────────────────────────
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
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    var settings by remember { mutableStateOf(DesktopDataStore.currentSettings) }
    var statusNotification by remember { mutableStateOf<String?>(null) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearWatchlistDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = SurfaceBase
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Header ────────────────────────────────────────────────────────────
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Customize playback, interface appearance, subtitles, and data storage",
                fontSize = 12.sp,
                color = TextSecondary
            )

            Spacer(Modifier.height(20.dp))

            // Notification Banner
            if (statusNotification != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AccentBlue.copy(0.12f),
                    border = BorderStroke(1.dp, AccentBlue.copy(0.3f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(statusNotification!!, color = TextPrimary, fontSize = 12.sp)
                        }
                        Icon(
                            Icons.Default.Close, null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp).clickable { statusNotification = null }
                        )
                    }
                }
            }

            // ── APPEARANCE & GENERAL ──────────────────────────────────────────────
            SettingsSection(title = "Appearance & General", icon = Icons.Default.Palette) {
                SettingSwitchRow(
                    title = "Dark Theme",
                    subtitle = "Enable sleek dark mode interface across all screens",
                    checked = settings.isDarkMode,
                    onCheckedChange = { isDark ->
                        DesktopDataStore.updateSettings { it.copy(isDarkMode = isDark) }
                        settings = DesktopDataStore.currentSettings
                        statusNotification = "Dark mode preference saved"
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── PLAYBACK & VIDEO QUALITY ──────────────────────────────────────────
            SettingsSection(title = "Player & Video Quality", icon = Icons.Default.PlayCircle) {
                var qualityDropdown by remember { mutableStateOf(false) }
                val qualities = listOf("Auto", "4K", "1080p", "720p", "480p", "360p")

                SettingDropdownRow(
                    title = "Default Stream Quality",
                    subtitle = "Preferred video resolution when launching streams",
                    value = settings.defaultQuality,
                    expanded = qualityDropdown,
                    onExpand = { qualityDropdown = true },
                    onDismiss = { qualityDropdown = false }
                ) {
                    qualities.forEach { q ->
                        DropdownMenuItem(
                            text = { Text(q, color = TextPrimary, fontSize = 13.sp) },
                            onClick = {
                                DesktopDataStore.updateSettings { it.copy(defaultQuality = q) }
                                settings = DesktopDataStore.currentSettings
                                qualityDropdown = false
                                statusNotification = "Default stream quality set to $q"
                            }
                        )
                    }
                }

                HorizontalDivider(color = BorderSubtle)

                SettingSwitchRow(
                    title = "Auto-Play Next Episode",
                    subtitle = "Automatically load the next episode when playback finishes",
                    checked = true,
                    onCheckedChange = {
                        statusNotification = "Auto-play setting updated"
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── SUBTITLES ─────────────────────────────────────────────────────────
            SettingsSection(title = "Subtitles & Audio", icon = Icons.Default.ClosedCaption) {
                var langDropdown by remember { mutableStateOf(false) }
                val languages = listOf("English", "Bengali", "Hindi", "Spanish", "French", "German", "Japanese", "Arabic")

                SettingDropdownRow(
                    title = "Preferred Subtitle Language",
                    subtitle = "Automatically select matching subtitle language tracks",
                    value = settings.subtitleLanguage,
                    expanded = langDropdown,
                    onExpand = { langDropdown = true },
                    onDismiss = { langDropdown = false }
                ) {
                    languages.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang, color = TextPrimary, fontSize = 13.sp) },
                            onClick = {
                                DesktopDataStore.updateSettings { it.copy(subtitleLanguage = lang) }
                                settings = DesktopDataStore.currentSettings
                                langDropdown = false
                                statusNotification = "Subtitle language set to $lang"
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── STORAGE & HISTORY ─────────────────────────────────────────────────
            SettingsSection(title = "Data & Storage Management", icon = Icons.Default.Storage) {
                SettingActionRow(
                    title = "Clear Watch History",
                    subtitle = "Remove all continue watching items and playback progress",
                    actionLabel = "Clear History",
                    isDestructive = true,
                    onClick = { showClearHistoryDialog = true }
                )

                HorizontalDivider(color = BorderSubtle)

                SettingActionRow(
                    title = "Clear Watchlist",
                    subtitle = "Remove all bookmarked movies and series from your watchlist",
                    actionLabel = "Clear Watchlist",
                    isDestructive = true,
                    onClick = { showClearWatchlistDialog = true }
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── ABOUT & SYSTEM INFO ───────────────────────────────────────────────
            SettingsSection(title = "About CloudStream Desktop", icon = Icons.Default.Info) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceLayer,
                        border = BorderStroke(1.dp, BorderDefault),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource("icon.png"),
                                contentDescription = "Logo",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text("CloudStream Windows Desktop", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(2.dp))
                        Text("Version 3.0.0 — Compose for Desktop & VLCj Native Engine", color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Native BDIX FTP support, CloudStream extensions, and hardware acceleration.", color = TextTertiary, fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }

        // ── Clear History Confirmation Modal ───────────────────────────────────────
        if (showClearHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showClearHistoryDialog = false },
                containerColor = SurfaceCard,
                shape = RoundedCornerShape(12.dp),
                title = { Text("Clear Watch History?", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to clear all your watch progress and continue watching history? This action cannot be undone.", color = TextSecondary, fontSize = 12.sp) },
                confirmButton = {
                    Button(
                        onClick = {
                            DesktopDataStore.clearHistory()
                            showClearHistoryDialog = false
                            statusNotification = "Watch history cleared successfully!"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCF6679)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Clear All History", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearHistoryDialog = false }, shape = RoundedCornerShape(6.dp)) {
                        Text("Cancel", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            )
        }

        // ── Clear Watchlist Confirmation Modal ─────────────────────────────────────
        if (showClearWatchlistDialog) {
            AlertDialog(
                onDismissRequest = { showClearWatchlistDialog = false },
                containerColor = SurfaceCard,
                shape = RoundedCornerShape(12.dp),
                title = { Text("Clear Watchlist?", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to remove all bookmarked items from your watchlist?", color = TextSecondary, fontSize = 12.sp) },
                confirmButton = {
                    Button(
                        onClick = {
                            DesktopDataStore.clearWatchlist()
                            showClearWatchlistDialog = false
                            statusNotification = "Watchlist cleared successfully!"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCF6679)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Clear Watchlist", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearWatchlistDialog = false }, shape = RoundedCornerShape(6.dp)) {
                        Text("Cancel", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            )
        }
    }
}

// ─── Settings Helper Components ───────────────────────────────────────────────

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(icon, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = TextSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentBlue,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = SurfaceLayer
            )
        )
    }
}

@Composable
private fun SettingDropdownRow(
    title: String,
    subtitle: String,
    value: String,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    dropdownItems: @Composable ColumnScope.() -> Unit
) {
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpand() }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = TextSecondary, fontSize = 11.sp)
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = SurfaceLayer,
                border = BorderStroke(1.dp, BorderDefault)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(value, color = AccentBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowDropDown, null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            modifier = Modifier.background(SurfaceCard).border(1.dp, BorderSubtle, RoundedCornerShape(6.dp)),
            content = dropdownItems
        )
    }
}

@Composable
private fun SettingActionRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = TextSecondary, fontSize = 11.sp)
        }
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isDestructive) Color(0xFFCF6679).copy(0.15f) else AccentBlue.copy(0.15f)
            ),
            border = BorderStroke(1.dp, if (isDestructive) Color(0xFFCF6679).copy(0.4f) else AccentBlue.copy(0.4f)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                actionLabel,
                fontSize = 11.sp,
                color = if (isDestructive) Color(0xFFCF6679) else AccentBlue,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
