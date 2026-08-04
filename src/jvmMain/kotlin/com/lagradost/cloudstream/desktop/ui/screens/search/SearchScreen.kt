package com.lagradost.cloudstream.desktop.ui.screens.search

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream.desktop.plugins.DesktopPluginLoader
import com.lagradost.cloudstream.desktop.storage.DesktopDataStore
import com.lagradost.cloudstream.desktop.ui.components.MediaCard
import com.lagradost.cloudstream.desktop.ui.navigation.DesktopScreen
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val POPULAR_SEARCH_TAGS = listOf(
    "Solo Leveling", "Naruto", "Bleach", "One Piece",
    "Demon Slayer", "Jujutsu Kaisen", "Marvel", "Batman", "Attack on Titan"
)

// Maps provider results grouped by provider name
data class ProviderSearchResult(
    val providerName: String,
    val items: List<SearchResponse>
)

// TvType display labels matching CloudStream Android
private val TV_TYPE_LABELS = listOf(
    null to "All",
    TvType.Movie to "Movies",
    TvType.TvSeries to "TV Series",
    TvType.Anime to "Anime",
    TvType.AsianDrama to "Asian Dramas",
    TvType.Cartoon to "Cartoons",
    TvType.Documentary to "Documentaries",
    TvType.Others to "Others",
    TvType.Live to "Live TV",
    TvType.AnimeMovie to "Anime Movies",
)

@Composable
fun SearchScreen(
    initialQuery: String = "",
    onNavigate: (DesktopScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val apis by DesktopPluginLoader.registeredApis.collectAsState()

    var searchHistoryState by remember { mutableStateOf(DesktopDataStore.currentSettings.searchHistory) }

    var searchQuery by remember { mutableStateOf(initialQuery) }
    // Results grouped by provider
    var providerResults by remember { mutableStateOf<List<ProviderSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf<TvType?>(null) }
    var selectedApi by remember { mutableStateOf<MainAPI?>(null) }
    var searchedCount by remember { mutableStateOf(0) }
    var totalCount by remember { mutableStateOf(0) }

    var activeSearchJob by remember { mutableStateOf<Job?>(null) }

    val apisToSearch = if (selectedApi != null) listOf(selectedApi!!) else apis

    // Filtered view per provider for the selected TvType
    val filteredProviderResults = remember(providerResults, selectedType) {
        if (selectedType == null) {
            providerResults
        } else {
            providerResults
                .map { it.copy(items = it.items.filter { item -> item.type == selectedType }) }
                .filter { it.items.isNotEmpty() }
        }
    }

    // All available TvTypes from current results
    val availableTypes = remember(providerResults) {
        providerResults.flatMap { it.items }.mapNotNull { it.type }.distinct()
    }

    val totalResultCount = filteredProviderResults.sumOf { it.items.size }

    fun executeSearch(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            providerResults = emptyList()
            isSearching = false
            return
        }

        activeSearchJob?.cancel()
        DesktopDataStore.saveSearchQuery(cleanQuery)
        searchHistoryState = DesktopDataStore.currentSettings.searchHistory

        activeSearchJob = coroutineScope.launch {
            isSearching = true
            searchError = null
            providerResults = emptyList()
            selectedType = null

            val currentApis = apisToSearch.toList()
            totalCount = currentApis.size
            searchedCount = 0

            val mutex = Mutex()
            val accumulated = mutableListOf<ProviderSearchResult>()

            // TRUE parallel: each provider gets its own coroutine,
            // state is updated via Mutex the INSTANT any provider finishes.
            // Fast providers appear immediately — slow ones don't block anything.
            val jobs = currentApis.map { api ->
                launch(Dispatchers.IO) {
                    val results = try {
                        withTimeout(10_000L) {
                            api.search(cleanQuery) ?: emptyList()
                        }
                    } catch (_: TimeoutCancellationException) { emptyList() }
                    catch (_: NotImplementedError) { emptyList() }
                    catch (_: Exception) { emptyList() }

                    mutex.withLock {
                        searchedCount++
                        if (results.isNotEmpty()) {
                            accumulated.add(
                                ProviderSearchResult(
                                    api.name,
                                    results.distinctBy { it.url }
                                )
                            )
                            // Publish to UI immediately on this provider's completion
                            withContext(Dispatchers.Main) {
                                providerResults = accumulated.toList()
                            }
                        }
                    }
                }
            }

            // Wait for all providers to complete
            jobs.joinAll()
            isSearching = false
        }
    }

    // Debounced live search — 150ms for snappy response
    LaunchedEffect(searchQuery) {
        activeSearchJob?.cancel()
        if (searchQuery.trim().length >= 2) {
            delay(150)
            executeSearch(searchQuery)
        } else if (searchQuery.isBlank()) {
            providerResults = emptyList()
            isSearching = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF101018))
    ) {
        // ── SEARCH BAR + PROVIDER FILTER ──────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D0D15))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search movies, anime, series…", color = Color.Gray, fontSize = 14.sp) },
                    leadingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                        }
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                activeSearchJob?.cancel()
                                searchQuery = ""
                                providerResults = emptyList()
                                isSearching = false
                            }) {
                                Icon(Icons.Default.Clear, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { executeSearch(searchQuery) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedContainerColor = Color(0xFF1A1A26),
                        unfocusedContainerColor = Color(0xFF161622)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // Provider filter button
                if (apis.size > 1) {
                    var providerDropdown by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { providerDropdown = true },
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selectedApi != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    else Color(0xFF1A1A26)
                                )
                                .size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Tune,
                                "Filter",
                                tint = if (selectedApi != null) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                        DropdownMenu(
                            expanded = providerDropdown,
                            onDismissRequest = { providerDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Providers (${apis.size})") },
                                leadingIcon = { Icon(Icons.Default.SelectAll, null) },
                                onClick = {
                                    selectedApi = null
                                    providerDropdown = false
                                    if (searchQuery.isNotBlank()) executeSearch(searchQuery)
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            apis.forEach { api ->
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier.size(8.dp).clip(CircleShape)
                                                .background(
                                                    if (api == selectedApi) MaterialTheme.colorScheme.primary else Color.Gray
                                                )
                                        )
                                    },
                                    text = { Text(api.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    trailingIcon = {
                                        if (api == selectedApi) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    },
                                    onClick = {
                                        selectedApi = api
                                        DesktopDataStore.updateSettings { s -> s.copy(selectedProvider = api.name) }
                                        providerDropdown = false
                                        if (searchQuery.isNotBlank()) executeSearch(searchQuery)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Progress bar when searching
            if (isSearching && totalCount > 0) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { searchedCount.toFloat() / totalCount.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)).height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.06f)
                )
            }
        }

        // ── TVTYPE FILTER TABS (only when results exist) ─────────────────────
        val visibleTypes = TV_TYPE_LABELS.filter { (type, _) ->
            type == null || availableTypes.contains(type)
        }
        if (providerResults.isNotEmpty() || isSearching) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D0D15))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                visibleTypes.forEach { (type, label) ->
                    val isSelected = selectedType == type
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedType = if (isSelected && type != null) null else type },
                        label = {
                            Text(
                                label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xFF1E1E2A),
                            labelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Color.White.copy(alpha = if (isSelected) 0f else 0.15f),
                            selectedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // Result count + active provider info
            if (totalResultCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$totalResultCount result${if (totalResultCount != 1) "s" else ""}",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (selectedApi != null) {
                        Text(" · ${selectedApi!!.name}", color = Color.Gray, fontSize = 12.sp)
                    }
                    if (isSearching) {
                        Spacer(Modifier.width(8.dp))
                        Text("Searching…", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }

        // ── MAIN CONTENT ─────────────────────────────────────────────────────
        when {
            // Error
            searchError != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(44.dp))
                        Text(searchError!!, color = MaterialTheme.colorScheme.error)
                        Button(onClick = { executeSearch(searchQuery) }) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Retry")
                        }
                    }
                }
            }

            // Blank query — History + Popular suggestions
            searchQuery.isBlank() -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (searchHistoryState.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Text("Recent Searches", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                }
                                TextButton(onClick = {
                                    DesktopDataStore.clearSearchHistory()
                                    searchHistoryState = emptyList()
                                }) {
                                    Text("Clear All", fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                        }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(searchHistoryState) { q ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF1E1E2A),
                                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.18f)),
                                        modifier = Modifier.clickable { searchQuery = q }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Default.History, null, tint = Color.Gray, modifier = Modifier.size(13.dp))
                                            Text(q, color = Color.White, fontSize = 13.sp)
                                            Icon(
                                                Icons.Default.Close, "Remove",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(13.dp).clickable {
                                                    DesktopDataStore.removeSearchQuery(q)
                                                    searchHistoryState = DesktopDataStore.currentSettings.searchHistory
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(20.dp)) }
                    }

                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text("Popular Searches", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                        }
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(POPULAR_SEARCH_TAGS) { tag ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF1E1E2A),
                                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f)),
                                    modifier = Modifier.clickable { searchQuery = tag }
                                ) {
                                    Text(tag, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Searching with no results yet — spinner
            isSearching && filteredProviderResults.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(44.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
                        Text(
                            "Searching ${apisToSearch.size} provider${if (apisToSearch.size != 1) "s" else ""}…",
                            color = Color.LightGray, fontSize = 13.sp
                        )
                    }
                }
            }

            // No results after search
            filteredProviderResults.isEmpty() && searchQuery.isNotBlank() && !isSearching -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.SearchOff, null, tint = Color.Gray, modifier = Modifier.size(54.dp))
                        Text("No results for \"$searchQuery\"", color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Try another keyword or switch provider", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            }

            // ── RESULTS: Grouped by provider with horizontal rows ─────────────
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredProviderResults, key = { it.providerName }) { providerResult ->
                        ProviderSection(
                            providerResult = providerResult,
                            onItemClick = { item ->
                                onNavigate(DesktopScreen.Result(
                                    url = item.url,
                                    apiName = item.apiName,
                                    searchResponse = item
                                ))
                            }
                        )
                    }

                    // Spinner at bottom while still searching more providers
                    if (isSearching) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                                    Text("Loading more providers ($searchedCount/$totalCount)…", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Provider Section: Header + Horizontal Card Row ────────────────────────────
@Composable
private fun ProviderSection(
    providerResult: ProviderSearchResult,
    onItemClick: (SearchResponse) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        // Provider name header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Text(
                text = providerResult.providerName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "(${providerResult.items.size})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }

        // Horizontal poster row
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(providerResult.items, key = { it.url }) { item ->
                MediaCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    width = 120.dp,
                    height = 178.dp
                )
            }
        }
    }
}
