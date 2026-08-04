package com.lagradost.cloudstream.desktop.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream.desktop.storage.DesktopDataStore

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    var settings by remember { mutableStateOf(DesktopDataStore.currentSettings) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = Color.White
        )
        Text(
            "Customize playback, appearance, and extensions",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(24.dp))

        // ── APPEARANCE ────────────────────────────────────────────────────────
        SettingsSection(title = "Appearance", icon = Icons.Default.Palette) {
            SettingSwitchRow(
                title = "Dark Theme",
                subtitle = "Enable sleek dark mode interface",
                checked = settings.isDarkMode,
                onCheckedChange = { isDark ->
                    DesktopDataStore.updateSettings { it.copy(isDarkMode = isDark) }
                    settings = DesktopDataStore.currentSettings
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── PLAYBACK ─────────────────────────────────────────────────────────
        SettingsSection(title = "Player & Video Quality", icon = Icons.Default.PlayCircle) {
            // Default Quality
            var qualityDropdown by remember { mutableStateOf(false) }
            val qualities = listOf("Auto", "1080p", "720p", "480p", "360p")

            SettingClickableRow(
                title = "Default Quality",
                subtitle = "Preferred video resolution when streaming",
                value = settings.defaultQuality,
                onClick = { qualityDropdown = true }
            ) {
                DropdownMenu(expanded = qualityDropdown, onDismissRequest = { qualityDropdown = false }) {
                    qualities.forEach { q ->
                        DropdownMenuItem(
                            text = { Text(q) },
                            onClick = {
                                DesktopDataStore.updateSettings { it.copy(defaultQuality = q) }
                                settings = DesktopDataStore.currentSettings
                                qualityDropdown = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── SUBTITLES ────────────────────────────────────────────────────────
        SettingsSection(title = "Subtitles", icon = Icons.Default.ClosedCaption) {
            var langDropdown by remember { mutableStateOf(false) }
            val languages = listOf("English", "Bengali", "Hindi", "Spanish", "French", "German", "Japanese", "Arabic")

            SettingClickableRow(
                title = "Preferred Subtitle Language",
                subtitle = "Automatically select matching subtitle tracks",
                value = settings.subtitleLanguage,
                onClick = { langDropdown = true }
            ) {
                DropdownMenu(expanded = langDropdown, onDismissRequest = { langDropdown = false }) {
                    languages.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang) },
                            onClick = {
                                DesktopDataStore.updateSettings { it.copy(subtitleLanguage = lang) }
                                settings = DesktopDataStore.currentSettings
                                langDropdown = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── EXTENSIONS & REPOSITORIES ─────────────────────────────────────────
        SettingsSection(title = "Extensions", icon = Icons.Default.Extension) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Plugin Repositories", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${settings.pluginRepositories.size} repository source${if (settings.pluginRepositories.size != 1) "s" else ""} active",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── ABOUT ─────────────────────────────────────────────────────────────
        SettingsSection(title = "About", icon = Icons.Default.Info) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("CloudStream Windows Desktop", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text("Version 3.0.0 — Ported with Compose for Desktop & VLCj", color = Color.Gray, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text("Supports all CloudStream extensions, BDIX media servers, and native VLC stream decoding.", color = Color.Gray.copy(alpha = 0.7f), fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

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
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2A))
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
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color.Gray, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun SettingClickableRow(
    title: String,
    subtitle: String,
    value: String,
    onClick: () -> Unit,
    dropdownMenu: @Composable () -> Unit
) {
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Color.Gray, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        dropdownMenu()
    }
}
