package com.lagradost.cloudstream.desktop.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream.desktop.plugins.DesktopPluginLoader
import com.lagradost.cloudstream.desktop.storage.DesktopDataStore
import com.lagradost.cloudstream.desktop.ui.components.ContinueWatchingCard
import com.lagradost.cloudstream.desktop.ui.components.HeroBanner
import com.lagradost.cloudstream.desktop.ui.components.HomeLoadingShimmer
import com.lagradost.cloudstream.desktop.ui.components.MediaCard
import com.lagradost.cloudstream.desktop.ui.components.TvTypeChips
import com.lagradost.cloudstream.desktop.ui.navigation.DesktopScreen
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onNavigate: (DesktopScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val registeredApis by DesktopPluginLoader.registeredApis.collectAsState()
    val watchHistory = DesktopDataStore.currentSettings.watchHistory

    var selectedApi by remember { mutableStateOf<MainAPI?>(null) }
    var homePageLists by remember { mutableStateOf<List<HomePageList>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf<TvType?>(null) }

    // Live clock formatted hh:mm a / HH:mm (matching home_clock in fragment_home_tv.xml)
    var currentTimeText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        while (isActive) {
            currentTimeText = sdf.format(Date())
            delay(1000)
        }
    }

    // Auto-select saved or first provider when APIs load
    LaunchedEffect(registeredApis) {
        if (registeredApis.isNotEmpty()) {
            val savedName = DesktopDataStore.currentSettings.selectedProvider
            val matching = registeredApis.find { it.name.equals(savedName, ignoreCase = true) }
            selectedApi = matching ?: registeredApis.first()
        }
    }

    fun loadHomeContent(api: MainAPI) {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            homePageLists = emptyList()
            try {
                val data = withContext(Dispatchers.IO) {
                    api.mainPage.mapNotNull { pageData ->
                        try {
                            api.getMainPage(1, MainPageRequest(
                                name = pageData.name,
                                data = pageData.data,
                                horizontalImages = pageData.horizontalImages
                            ))
                        } catch (_: Exception) { null }
                    }
                }
                homePageLists = data.flatMap { it.items }
            } catch (e: Exception) {
                errorMessage = "Failed to load from ${api.name}: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedApi) {
        selectedApi?.let { loadHomeContent(it) }
    }

    // Filtered lists by TvType
    val filteredLists = remember(homePageLists, selectedType) {
        if (selectedType == null) homePageLists
        else homePageLists.map { row ->
            row.copy(list = row.list.filter { it.type == selectedType })
        }.filter { it.list.isNotEmpty() }
    }

    // Featured items from first non-empty row
    val featuredItems = remember(homePageLists) {
        homePageLists.firstOrNull()?.list?.take(8) ?: emptyList()
    }

    // Random play picker (matching home_random_button_tv)
    fun pickRandomItem() {
        val allItems = homePageLists.flatMap { it.list }
        if (allItems.isNotEmpty()) {
            val randomItem = allItems.random()
            onNavigate(DesktopScreen.Result(
                url = randomItem.url,
                apiName = randomItem.apiName,
                searchResponse = randomItem
            ))
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0D0D12))) {
        if (registeredApis.isEmpty()) {
            // No providers installed
            NoProvidersState(onNavigate)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // ── HERO & HEADER ───────────────────────────────────────────
                item {
                    Column {
                        // Top header bar matching fragment_home_tv.xml
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Logo + Live Clock (home_clock)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(
                                        "CloudStream",
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.ExtraBold
                                        ),
                                        color = Color.White
                                    )
                                    Text(
                                        selectedApi?.name ?: "Select a provider",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (currentTimeText.isNotEmpty()) {
                                    Spacer(Modifier.width(20.dp))
                                    Surface(
                                        color = Color.White.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            currentTimeText,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }

                            // Right: Provider Picker + Reload + Random + Search
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Provider picker button
                                var providerDropdown by remember { mutableStateOf(false) }
                                Box {
                                    OutlinedButton(
                                        onClick = { providerDropdown = true },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                                    ) {
                                        Icon(Icons.Default.Source, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            selectedApi?.name ?: "Provider",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 150.dp)
                                        )
                                        Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp))
                                    }
                                    DropdownMenu(
                                        expanded = providerDropdown,
                                        onDismissRequest = { providerDropdown = false }
                                    ) {
                                        registeredApis.forEach { api ->
                                            DropdownMenuItem(
                                                leadingIcon = {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                if (api == selectedApi) MaterialTheme.colorScheme.primary
                                                                else Color.Gray
                                                            )
                                                    )
                                                },
                                                text = { Text(api.name) },
                                                onClick = {
                                                    selectedApi = api
                                                    DesktopDataStore.updateSettings { settings ->
                                                        settings.copy(selectedProvider = api.name)
                                                    }
                                                    providerDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Reload Provider Icon
                                IconButton(
                                    onClick = { selectedApi?.let { loadHomeContent(it) } },
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.1f))
                                ) {
                                    Icon(Icons.Default.Refresh, "Reload Provider", tint = Color.White)
                                }

                                // Random Play "Surprise Me" Icon (home_random_button_tv)
                                IconButton(
                                    onClick = { pickRandomItem() },
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.1f))
                                ) {
                                    Icon(Icons.Default.Casino, "Random Pick", tint = MaterialTheme.colorScheme.primary)
                                }

                                // Search shortcut
                                IconButton(
                                    onClick = { onNavigate(DesktopScreen.Search()) },
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.1f))
                                ) {
                                    Icon(Icons.Default.Search, "Search", tint = Color.White)
                                }
                            }
                        }

                        // Hero Banner Carousel
                        if (featuredItems.isNotEmpty() && !isLoading) {
                            HeroBanner(
                                items = featuredItems,
                                onItemClick = { item ->
                                    onNavigate(DesktopScreen.Result(
                                        url = item.url,
                                        apiName = item.apiName,
                                        searchResponse = item
                                    ))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                            )
                            Spacer(Modifier.height(20.dp))
                        }

                        // TvType filter chips
                        TvTypeChips(
                            selected = selectedType,
                            onSelect = { selectedType = it },
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )

                        Spacer(Modifier.height(16.dp))
                    }
                }

                // ── LOADING / ERROR STATES ──────────────────────────────────
                if (isLoading) {
                    item {
                        HomeLoadingShimmer()
                    }
                } else if (errorMessage != null) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(260.dp).padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(44.dp))
                                Spacer(Modifier.height(12.dp))
                                Text(errorMessage!!, color = MaterialTheme.colorScheme.error, maxLines = 3, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Spacer(Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(onClick = { selectedApi?.let { loadHomeContent(it) } }) {
                                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Reload")
                                    }
                                    selectedApi?.mainUrl?.let { url ->
                                        OutlinedButton(
                                            onClick = { try { Desktop.getDesktop().browse(URI(url)) } catch (_: Exception) {} },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                        ) {
                                            Icon(Icons.Default.Public, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Open in Browser")
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // ── CONTINUE WATCHING ───────────────────────────────────
                    if (watchHistory.isNotEmpty()) {
                        item {
                            SectionRow(
                                title = "Continue Watching",
                                icon = Icons.Default.WatchLater,
                                paddingStart = 24.dp
                            ) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(watchHistory) { progress ->
                                        ContinueWatchingCard(
                                            progress = progress,
                                            onClick = {
                                                onNavigate(DesktopScreen.Result(
                                                    url = progress.url,
                                                    apiName = selectedApi?.name ?: ""
                                                ))
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    // ── CATALOGUE ROWS ──────────────────────────────────────
                    items(filteredLists) { pageList ->
                        if (pageList.list.isNotEmpty()) {
                            SectionRow(
                                title = pageList.name,
                                paddingStart = 24.dp
                            ) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(pageList.list) { item ->
                                        MediaCard(
                                            item = item,
                                            onClick = {
                                                onNavigate(DesktopScreen.Result(
                                                    url = item.url,
                                                    apiName = item.apiName,
                                                    searchResponse = item
                                                ))
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SectionRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    paddingStart: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.padding(start = paddingStart, bottom = 12.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }
        content()
    }
}

@Composable
private fun NoProvidersState(onNavigate: (DesktopScreen) -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(40.dp)
        ) {
            Icon(
                Icons.Default.Extension,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(80.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "No Providers Installed",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Install a CloudStream plugin extension to browse movies, anime, and TV series.",
                color = Color.Gray,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onNavigate(DesktopScreen.Plugins) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Extension, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Browse Extensions", fontWeight = FontWeight.Bold)
            }
        }
    }
}
