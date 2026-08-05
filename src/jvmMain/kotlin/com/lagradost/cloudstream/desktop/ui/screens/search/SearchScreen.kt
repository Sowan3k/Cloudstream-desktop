package com.lagradost.cloudstream.desktop.ui.screens.search

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
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

// ─── Windows 11 Color Tokens ──────────────────────────────────────────────────
private val SurfaceBase    = Color(0xFF1C1C1C)
private val SurfaceLayer   = Color(0xFF242424)
private val SurfaceCard    = Color(0xFF2C2C2C)
private val BorderSubtle   = Color(0x18FFFFFF)
private val BorderDefault  = Color(0x26FFFFFF)
private val TextPrimary    = Color(0xFFFFFFFF)
private val TextSecondary  = Color(0x99FFFFFF)
private val TextTertiary   = Color(0x66FFFFFF)
private val AccentBlue     = Color(0xFF0078D4)

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

// ─── Singleton State to Persist Search Results Across Navigation ───────────────
object DesktopSearchState {
    var searchQuery by mutableStateOf("")
    var providerResults by mutableStateOf<List<ProviderSearchResult>>(emptyList())
    var isSearching by mutableStateOf(false)
    var searchError by mutableStateOf<String?>(null)
    var selectedType by mutableStateOf<TvType?>(null)
    var selectedApi by mutableStateOf<MainAPI?>(null)
    var searchedCount by mutableStateOf(0)
    var totalCount by mutableStateOf(0)
    var activeSearchJob: Job? = null

    fun clear() {
        activeSearchJob?.cancel()
        activeSearchJob = null
        searchQuery = ""
        providerResults = emptyList()
        isSearching = false
        searchError = null
        selectedType = null
        searchedCount = 0
        totalCount = 0
    }
}

@Composable
fun SearchScreen(
    initialQuery: String = "",
    onNavigate: (DesktopScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val apis by DesktopPluginLoader.registeredApis.collectAsState()

    var searchHistoryState by remember { mutableStateOf(DesktopDataStore.currentSettings.searchHistory) }

    val searchQuery = DesktopSearchState.searchQuery
    val providerResults = DesktopSearchState.providerResults
    val isSearching = DesktopSearchState.isSearching
    val searchError = DesktopSearchState.searchError
    var selectedType by DesktopSearchState::selectedType
    var selectedApi by DesktopSearchState::selectedApi
    val searchedCount = DesktopSearchState.searchedCount
    val totalCount = DesktopSearchState.totalCount

    var lastExecutedQuery by remember { mutableStateOf(searchQuery) }

    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        searchFocusRequester.requestFocus()
    }

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

    val availableTypes = remember(providerResults) {
        providerResults.flatMap { it.items }.mapNotNull { it.type }.distinct()
    }

    val totalResultCount = filteredProviderResults.sumOf { it.items.size }

    fun executeSearch(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            DesktopSearchState.providerResults = emptyList()
            DesktopSearchState.isSearching = false
            return
        }

        DesktopSearchState.activeSearchJob?.cancel()
        DesktopDataStore.saveSearchQuery(cleanQuery)
        searchHistoryState = DesktopDataStore.currentSettings.searchHistory

        DesktopSearchState.activeSearchJob = coroutineScope.launch {
            DesktopSearchState.isSearching = true
            DesktopSearchState.searchError = null
            DesktopSearchState.providerResults = emptyList()
            DesktopSearchState.selectedType = null

            val currentApis = apisToSearch.toList()
            DesktopSearchState.totalCount = currentApis.size
            DesktopSearchState.searchedCount = 0

            val mutex = Mutex()
            val accumulated = mutableListOf<ProviderSearchResult>()

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
                        DesktopSearchState.searchedCount++
                        if (results.isNotEmpty()) {
                            accumulated.add(
                                ProviderSearchResult(
                                    api.name,
                                    results.distinctBy { it.url }
                                )
                            )
                            withContext(Dispatchers.Main) {
                                DesktopSearchState.providerResults = accumulated.toList()
                            }
                        }
                    }
                }
            }

            jobs.joinAll()
            DesktopSearchState.isSearching = false
        }
    }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank() && initialQuery != searchQuery) {
            DesktopSearchState.searchQuery = initialQuery
            lastExecutedQuery = initialQuery
            executeSearch(initialQuery)
        }
    }

    // Debounced live search
    LaunchedEffect(searchQuery) {
        if (searchQuery.trim().length >= 2) {
            if (searchQuery != lastExecutedQuery || providerResults.isEmpty()) {
                delay(150)
                lastExecutedQuery = searchQuery
                executeSearch(searchQuery)
            }
        } else if (searchQuery.isBlank()) {
            lastExecutedQuery = ""
            DesktopSearchState.providerResults = emptyList()
            DesktopSearchState.isSearching = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceBase)
    ) {
        // ── HEADER & SEARCH BAR ─────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Search",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 22.sp,
                        color = TextPrimary
                    )
                    Text(
                        if (selectedApi != null) "Searching ${selectedApi!!.name}" else "Searching all providers",
                        fontSize = 12.sp,
                        color = TextTertiary
                    )
                }

                // Provider filter dropdown button
                if (apis.size > 1) {
                    var providerDropdown by remember { mutableStateOf(false) }
                    Box {
                        Surface(
                            onClick = { providerDropdown = true },
                            shape = RoundedCornerShape(6.dp),
                            color = SurfaceLayer,
                            border = BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.FilterList, null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                                Text(
                                    selectedApi?.name ?: "All Providers",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 130.dp)
                                )
                                Icon(Icons.Default.KeyboardArrowDown, null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                            }
                        }
                        DropdownMenu(
                            expanded = providerDropdown,
                            onDismissRequest = { providerDropdown = false },
                            modifier = Modifier.background(SurfaceCard).border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "All Providers (${apis.size})",
                                        color = if (selectedApi == null) AccentBlue else TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (selectedApi == null) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (selectedApi == null) ({
                                    Icon(Icons.Default.Check, null, tint = AccentBlue, modifier = Modifier.size(14.dp))
                                }) else null,
                                onClick = {
                                    selectedApi = null
                                    providerDropdown = false
                                    if (searchQuery.isNotBlank()) executeSearch(searchQuery)
                                }
                            )
                            HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 4.dp))
                            apis.forEach { api ->
                                val isSelected = api == selectedApi
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            api.name,
                                            color = if (isSelected) AccentBlue else TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingIcon = if (isSelected) ({
                                        Icon(Icons.Default.Check, null, tint = AccentBlue, modifier = Modifier.size(14.dp))
                                    }) else null,
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

            // Search input field
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = SurfaceLayer,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth().height(36.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            color = AccentBlue,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Search, null, tint = TextTertiary, modifier = Modifier.size(15.dp))
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { DesktopSearchState.searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { executeSearch(searchQuery) }),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text("Search movies, series, anime...", color = TextTertiary, fontSize = 13.sp)
                                }
                                innerTextField()
                            }
                        },
                        modifier = Modifier.weight(1f).focusRequester(searchFocusRequester)
                    )
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            Icons.Default.Close, null,
                            tint = TextTertiary,
                            modifier = Modifier.size(14.dp).clickable {
                                lastExecutedQuery = ""
                                DesktopSearchState.clear()
                            }
                        )
                    }
                }
            }

            // Linear Progress Indicator when searching
            if (isSearching && totalCount > 0) {
                LinearProgressIndicator(
                    progress = { searchedCount.toFloat() / totalCount.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(1.dp)).height(2.dp),
                    color = AccentBlue,
                    trackColor = Color.White.copy(alpha = 0.06f)
                )
            }
        }

        // ── TVTYPE FILTER CHIPS ─────────────────────────────────────────────
        val visibleTypes = TV_TYPE_LABELS.filter { (type, _) ->
            type == null || availableTypes.contains(type)
        }
        if (providerResults.isNotEmpty() || isSearching) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                visibleTypes.forEach { (type, label) ->
                    val isSelected = selectedType == type
                    val textColor by animateColorAsState(if (isSelected) TextPrimary else TextTertiary, tween(150))

                    Surface(
                        onClick = { selectedType = if (isSelected && type != null) null else type },
                        shape = RoundedCornerShape(5.dp),
                        color = if (isSelected) AccentBlue.copy(0.12f) else Color.Transparent,
                        border = BorderStroke(1.dp, if (isSelected) AccentBlue.copy(0.5f) else BorderSubtle)
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            color = textColor,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            if (totalResultCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "$totalResultCount result${if (totalResultCount != 1) "s" else ""}",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (selectedApi != null) {
                        Text("•  ${selectedApi!!.name}", color = TextTertiary, fontSize = 12.sp)
                    }
                    if (isSearching) {
                        Text("•  Searching ($searchedCount/$totalCount)...", color = TextTertiary, fontSize = 12.sp)
                    }
                }
            } else {
                Spacer(Modifier.height(10.dp))
            }
        }

        HorizontalDivider(color = BorderSubtle)

        // ── MAIN CONTENT ─────────────────────────────────────────────────────
        when {
            searchError != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFCF6679), modifier = Modifier.size(36.dp))
                        Text(searchError!!, color = TextSecondary, fontSize = 13.sp)
                        Surface(
                            onClick = { executeSearch(searchQuery) },
                            shape = RoundedCornerShape(6.dp),
                            color = SurfaceLayer,
                            border = BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Refresh, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                                Text("Retry", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Blank query — Recent & Popular suggestions
            searchQuery.isBlank() -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    if (searchHistoryState.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Recent Searches",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    "Clear All",
                                    fontSize = 12.sp,
                                    color = TextTertiary,
                                    modifier = Modifier.clickable {
                                        DesktopDataStore.clearSearchHistory()
                                        searchHistoryState = emptyList()
                                    }
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(searchHistoryState) { q ->
                                    Win11TagChip(
                                        label = q,
                                        isHistory = true,
                                        onClick = { DesktopSearchState.searchQuery = q },
                                        onRemove = {
                                            DesktopDataStore.removeSearchQuery(q)
                                            searchHistoryState = DesktopDataStore.currentSettings.searchHistory
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            "Popular Searches",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(POPULAR_SEARCH_TAGS) { tag ->
                                Win11TagChip(
                                    label = tag,
                                    isHistory = false,
                                    onClick = { DesktopSearchState.searchQuery = tag }
                                )
                            }
                        }
                    }
                }
            }

            // Searching with no results yet
            isSearching && filteredProviderResults.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = AccentBlue,
                            strokeWidth = 2.dp
                        )
                        Text(
                            "Searching ${apisToSearch.size} provider${if (apisToSearch.size != 1) "s" else ""}...",
                            color = TextTertiary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // No results after search
            filteredProviderResults.isEmpty() && searchQuery.isNotBlank() && !isSearching -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.SearchOff, null, tint = TextTertiary, modifier = Modifier.size(38.dp))
                        Text(
                            "No results for \"$searchQuery\"",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("Try a different term or select another provider", color = TextTertiary, fontSize = 12.sp)
                    }
                }
            }

            // Results grouped by provider
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(filteredProviderResults) { providerResult ->
                        Win11SearchProviderSection(
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

                    if (isSearching) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AccentBlue, strokeWidth = 2.dp)
                                    Text("Searching more providers ($searchedCount/$totalCount)...", color = TextTertiary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Tag Chip Component ───────────────────────────────────────────────────────
@Composable
private fun Win11TagChip(
    label: String,
    isHistory: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, if (hovered) BorderDefault else BorderSubtle),
        modifier = Modifier.hoverable(interactionSource)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isHistory) {
                Icon(Icons.Default.History, null, tint = TextTertiary, modifier = Modifier.size(13.dp))
            }
            Text(label, color = TextSecondary, fontSize = 12.sp)
            if (isHistory && onRemove != null) {
                Icon(
                    Icons.Default.Close, "Remove",
                    tint = TextTertiary,
                    modifier = Modifier.size(12.dp).clickable { onRemove() }
                )
            }
        }
    }
}

// ─── Provider Section Component ───────────────────────────────────────────────
@Composable
private fun Win11SearchProviderSection(
    providerResult: ProviderSearchResult,
    onItemClick: (SearchResponse) -> Unit
) {
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                providerResult.providerName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.White.copy(alpha = 0.06f)
            ) {
                Text(
                    providerResult.items.size.toString(),
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    fontSize = 11.sp,
                    color = TextTertiary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .hoverable(interactionSource),
            contentAlignment = Alignment.CenterStart
        ) {
            LazyRow(
                state = lazyListState,
                contentPadding = PaddingValues(horizontal = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(providerResult.items) { item ->
                    MediaCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        width = 130.dp,
                        height = 190.dp
                    )
                }
            }

            // Left scroll arrow
            androidx.compose.animation.AnimatedVisibility(
                visible = isHovered && lazyListState.canScrollBackward,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
            ) {
                Surface(
                    onClick = {
                        coroutineScope.launch {
                            lazyListState.animateScrollBy(-500f)
                        }
                    },
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(0.75f),
                    border = BorderStroke(1.dp, Color.White.copy(0.25f)),
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Scroll Left",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Right scroll arrow
            androidx.compose.animation.AnimatedVisibility(
                visible = isHovered && lazyListState.canScrollForward,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
            ) {
                Surface(
                    onClick = {
                        coroutineScope.launch {
                            lazyListState.animateScrollBy(500f)
                        }
                    },
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(0.75f),
                    border = BorderStroke(1.dp, Color.White.copy(0.25f)),
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Scroll Right",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
