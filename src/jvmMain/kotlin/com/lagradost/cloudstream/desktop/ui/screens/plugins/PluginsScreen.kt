package com.lagradost.cloudstream.desktop.ui.screens.plugins

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream.desktop.plugins.DesktopPluginLoader
import com.lagradost.cloudstream.desktop.storage.DesktopDataStore
import com.lagradost.cloudstream.desktop.ui.theme.*
import com.lagradost.cloudstream3.TvType
import kotlinx.coroutines.launch

private data class PresetRepo(
    val name: String,
    val description: String,
    val url: String,
    val isRecommended: Boolean = false
)

private val PRESET_REPOSITORIES = listOf(
    PresetRepo(
        name = "Nehal CloudStream Repo",
        description = "Native BDIX, DiscoveryFTP, SamFTP & Bengali/Hindi streaming providers",
        url = "https://raw.githubusercontent.com/nehalDIU/nehal-CloudStream/master/repo.json",
        isRecommended = true
    ),
    PresetRepo(
        name = "CloudStream Official English Repo",
        description = "Official English community extensions for movies, TV series and anime",
        url = "https://raw.githubusercontent.com/recloudstream/cloudstream-extensions/master/repo.json"
    ),
    PresetRepo(
        name = "CloudStream Multi-Language Repo",
        description = "Multi-language providers for global streaming sources",
        url = "https://raw.githubusercontent.com/recloudstream/cloudstream-extensions-multilingual/master/repo.json"
    )
)

@Composable
fun PluginsScreen(
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val apis by DesktopPluginLoader.registeredApis.collectAsState()
    val isSyncing by DesktopPluginLoader.isSyncing.collectAsState()

    val currentSettings = DesktopDataStore.currentSettings
    val savedRepos = remember(currentSettings) { currentSettings.pluginRepositories }

    var selectedTab by remember { mutableStateOf(0) } // 0: Installed, 1: Repositories
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf<TvType?>(null) }

    var repoUrlInput by remember { mutableStateOf("https://raw.githubusercontent.com/nehalDIU/nehal-CloudStream/master/repo.json") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showAddRepoDialog by remember { mutableStateOf(false) }

    val filteredApis = remember(apis, searchQuery, selectedTypeFilter) {
        apis.filter { api ->
            val matchesQuery = searchQuery.isBlank() ||
                    api.name.contains(searchQuery, ignoreCase = true) ||
                    api.mainUrl.contains(searchQuery, ignoreCase = true)
            val matchesType = selectedTypeFilter == null || api.supportedTypes.contains(selectedTypeFilter)
            matchesQuery && matchesType
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = SurfaceBase
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 20.dp)
        ) {
            // ── Top Header Bar ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Extensions & Repositories",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${apis.size} active provider plugin${if (apis.size != 1) "s" else ""} loaded",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Reload Local Plugins Button
                    Surface(
                        onClick = {
                            coroutineScope.launch {
                                DesktopPluginLoader.loadAllLocalPlugins()
                            }
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = SurfaceLayer,
                        border = BorderStroke(1.dp, BorderDefault)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Refresh, "Reload", tint = TextPrimary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Refresh Local", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        }
                    }

                    // Add Repository Button
                    Button(
                        onClick = { showAddRepoDialog = true },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent)
                    ) {
                        Icon(Icons.Default.Add, "Add Repo", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Repository", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Search Bar & Filter Chips ────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Search Box
                Surface(
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = SurfaceLayer,
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text("Search extensions by name or domain...", color = TextTertiary, fontSize = 13.sp)
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 13.sp),
                                cursorBrush = SolidColor(Accent),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                Icons.Default.Close, null,
                                tint = TextTertiary,
                                modifier = Modifier.size(16.dp).clickable { searchQuery = "" }
                            )
                        }
                    }
                }

                // Type Filter Chips
                val typeChips = listOf(
                    null to "All Types",
                    TvType.Movie to "Movies",
                    TvType.TvSeries to "Series",
                    TvType.Anime to "Anime",
                    TvType.Live to "Live TV"
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(typeChips) { (type, label) ->
                        val isSelected = selectedTypeFilter == type
                        val textColor by animateColorAsState(if (isSelected) TextPrimary else TextSecondary, tween(150))
                        val bgColor by animateColorAsState(if (isSelected) Accent.copy(0.15f) else SurfaceLayer, tween(150))

                        Surface(
                            onClick = { selectedTypeFilter = type },
                            shape = RoundedCornerShape(6.dp),
                            color = bgColor,
                            border = BorderStroke(1.dp, if (isSelected) Accent.copy(0.5f) else BorderSubtle)
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                fontSize = 12.sp,
                                color = textColor,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Tabs Header ──────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                listOf(
                    "Installed Extensions (${filteredApis.size})" to 0,
                    "Repositories (${savedRepos.size})" to 1
                ).forEach { (label, tabIndex) ->
                    val isSelected = selectedTab == tabIndex
                    val textColor by animateColorAsState(if (isSelected) TextPrimary else TextTertiary, tween(150))
                    val underlineColor by animateColorAsState(if (isSelected) Accent else Color.Transparent, tween(200))

                    Box(
                        modifier = Modifier
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = { selectedTab = tabIndex }
                            )
                            .drawBehind {
                                if (isSelected) {
                                    drawRect(
                                        color = underlineColor,
                                        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 2.dp.toPx()),
                                        size = androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx())
                                    )
                                }
                            }
                            .padding(bottom = 8.dp)
                    ) {
                        Text(
                            label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = textColor
                        )
                    }
                }
            }

            HorizontalDivider(color = BorderSubtle)

            Spacer(Modifier.height(16.dp))

            // ── Status Message Banner ────────────────────────────────────────────────
            if (statusMessage != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Accent.copy(0.12f),
                    border = BorderStroke(1.dp, Accent.copy(0.3f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = AccentText, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(statusMessage!!, color = TextPrimary, fontSize = 12.sp)
                        }
                        Icon(
                            Icons.Default.Close, null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp).clickable { statusMessage = null }
                        )
                    }
                }
            }

            // ── Tab Content ──────────────────────────────────────────────────────────
            when (selectedTab) {
                0 -> {
                    // Installed Extensions List
                    if (filteredApis.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ExtensionOff, null, tint = TextTertiary, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    if (searchQuery.isNotBlank()) "No extensions matching \"$searchQuery\""
                                    else "No extensions loaded. Add a repository URL to fetch stream providers.",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredApis, key = { it.name + it.mainUrl }) { api ->
                                ExtensionCard(api = api)
                            }
                        }
                    }
                }

                1 -> {
                    // Repositories List
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Text("Active Repositories", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        items(savedRepos) { repoUrl ->
                            RepositoryCard(
                                url = repoUrl,
                                isSyncing = isSyncing,
                                onSync = {
                                    coroutineScope.launch {
                                        statusMessage = "Syncing repository: $repoUrl..."
                                        val loaded = DesktopPluginLoader.loadRepository(repoUrl)
                                        statusMessage = "Loaded ${loaded.size} extensions from repository!"
                                    }
                                },
                                onDelete = {
                                    DesktopDataStore.updateSettings { settings ->
                                        settings.copy(pluginRepositories = settings.pluginRepositories - repoUrl)
                                    }
                                }
                            )
                        }

                        item {
                            Spacer(Modifier.height(16.dp))
                            Text("Preset Repositories", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        items(PRESET_REPOSITORIES) { preset ->
                            val isAdded = savedRepos.contains(preset.url)
                            PresetRepoCard(
                                preset = preset,
                                isAdded = isAdded,
                                onAdd = {
                                    coroutineScope.launch {
                                        statusMessage = "Fetching ${preset.name}..."
                                        val loaded = DesktopPluginLoader.loadRepository(preset.url)
                                        DesktopDataStore.updateSettings { settings ->
                                            if (!settings.pluginRepositories.contains(preset.url)) {
                                                settings.copy(pluginRepositories = settings.pluginRepositories + preset.url)
                                            } else settings
                                        }
                                        statusMessage = "Loaded ${loaded.size} extensions from ${preset.name}!"
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // ── Add Repository Dialog Modal ──────────────────────────────────────────────
        if (showAddRepoDialog) {
            AlertDialog(
                onDismissRequest = { showAddRepoDialog = false },
                containerColor = SurfaceCard,
                shape = RoundedCornerShape(12.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDownload, null, tint = AccentText, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Add Extension Repository", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Enter the URL of a CloudStream repository JSON manifest to fetch and load provider plugins:",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SurfaceLayer,
                            border = BorderStroke(1.dp, BorderDefault),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                                BasicTextField(
                                    value = repoUrlInput,
                                    onValueChange = { repoUrlInput = it },
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 13.sp),
                                    cursorBrush = SolidColor(Accent),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = repoUrlInput.isNotBlank() && !isSyncing,
                        onClick = {
                            val target = repoUrlInput.trim()
                            showAddRepoDialog = false
                            coroutineScope.launch {
                                statusMessage = "Fetching repository..."
                                val loaded = DesktopPluginLoader.loadRepository(target)
                                DesktopDataStore.updateSettings { settings ->
                                    if (!settings.pluginRepositories.contains(target)) {
                                        settings.copy(pluginRepositories = settings.pluginRepositories + target)
                                    } else settings
                                }
                                statusMessage = "Successfully loaded ${loaded.size} extensions!"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Fetch & Add", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showAddRepoDialog = false },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Cancel", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            )
        }
    }
}

// ─── Extension Card Component ────────────────────────────────────────────────

@Composable
private fun ExtensionCard(
    api: com.lagradost.cloudstream3.MainAPI
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, if (hovered) BorderDefault else BorderSubtle),
        modifier = Modifier.fillMaxWidth().hoverable(interactionSource)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Extension Plugin Icon Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Accent.copy(0.15f),
                    border = BorderStroke(1.dp, Accent.copy(0.3f)),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Extension, null, tint = AccentText, modifier = Modifier.size(22.dp))
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            api.name,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        // Active Badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF107C41),
                            border = BorderStroke(1.dp, Color(0xFF107C41).copy(0.4f))
                        ) {
                            Text(
                                "Active",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                fontSize = 10.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            api.mainUrl.substringAfter("://").substringBefore("/"),
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                        Text("•", color = TextTertiary, fontSize = 11.sp)
                        Text(
                            "Language: ${api.lang.uppercase()}",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    // Supported Types Pills
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(api.supportedTypes.toList()) { type ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = SurfaceLayer,
                                border = BorderStroke(1.dp, BorderSubtle)
                            ) {
                                Text(
                                    type.name,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Repository Card Component ───────────────────────────────────────────────

@Composable
private fun RepositoryCard(
    url: String,
    isSyncing: Boolean,
    onSync: () -> Unit,
    onDelete: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, if (hovered) BorderDefault else BorderSubtle),
        modifier = Modifier.fillMaxWidth().hoverable(interactionSource)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceLayer,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Dns, null, tint = AccentText, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        url.substringAfterLast("/").substringBefore("?").ifBlank { "Repository Manifest" },
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        url,
                        color = TextTertiary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = onSync, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, "Sync Repository", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, "Remove Repository", tint = ErrorColor, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ─── Preset Repo Card Component ──────────────────────────────────────────────

@Composable
private fun PresetRepoCard(
    preset: PresetRepo,
    isAdded: Boolean,
    onAdd: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, if (hovered) BorderDefault else BorderSubtle),
        modifier = Modifier.fillMaxWidth().hoverable(interactionSource)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (preset.isRecommended) Accent.copy(0.15f) else SurfaceLayer,
                    border = BorderStroke(1.dp, if (preset.isRecommended) Accent.copy(0.3f) else BorderSubtle),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (preset.isRecommended) Icons.Default.Star else Icons.Default.CloudDownload,
                            null,
                            tint = if (preset.isRecommended) AccentText else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            preset.name,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        if (preset.isRecommended) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Accent.copy(0.15f),
                                border = BorderStroke(1.dp, Accent.copy(0.4f))
                            ) {
                                Text(
                                    "Recommended",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    fontSize = 10.sp,
                                    color = AccentText,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(3.dp))

                    Text(
                        preset.description,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            if (isAdded) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF107C41),
                    border = BorderStroke(1.dp, Color(0xFF107C41).copy(0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Added", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = onAdd,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Repo", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
