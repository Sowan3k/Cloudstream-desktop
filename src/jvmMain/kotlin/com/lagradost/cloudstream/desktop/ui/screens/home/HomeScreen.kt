package com.lagradost.cloudstream.desktop.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.lagradost.cloudstream.desktop.plugins.DesktopPluginLoader
import com.lagradost.cloudstream.desktop.storage.DesktopDataStore
import com.lagradost.cloudstream.desktop.storage.WatchProgress
import com.lagradost.cloudstream.desktop.ui.components.*
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

// ─── Win11 Design Tokens ──────────────────────────────────────────────────────
private val BgBase       = Color(0xFF1C1C1C)
private val BgLayer      = Color(0xFF242424)
private val BgCard       = Color(0xFF2C2C2C)
private val BorderSubtle = Color(0x18FFFFFF)
private val BorderDef    = Color(0x26FFFFFF)
private val TxtPrimary   = Color(0xFFFFFFFF)
private val TxtSecondary = Color(0x99FFFFFF)
private val TxtTertiary  = Color(0x66FFFFFF)
private val Accent       = Color(0xFF0078D4)

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
                errorMessage = "Failed to load: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedApi) {
        selectedApi?.let { loadHomeContent(it) }
    }

    val filteredLists = remember(homePageLists, selectedType) {
        if (selectedType == null) homePageLists
        else homePageLists.map { row ->
            row.copy(list = row.list.filter { it.type == selectedType })
        }.filter { it.list.isNotEmpty() }
    }

    val featuredItems = remember(homePageLists) {
        homePageLists.firstOrNull()?.list?.take(8) ?: emptyList()
    }

    fun pickRandomItem() {
        val allItems = homePageLists.flatMap { it.list }
        if (allItems.isNotEmpty()) {
            val item = allItems.random()
            onNavigate(DesktopScreen.Result(url = item.url, apiName = item.apiName, searchResponse = item))
        }
    }

    Box(modifier = modifier.fillMaxSize().background(BgBase)) {
        if (registeredApis.isEmpty()) {
            Win11NoProviders(onNavigate)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {

                // ── HEADER ────────────────────────────────────────────────────
                item {
                    Win11HomeHeader(
                        selectedApi = selectedApi,
                        registeredApis = registeredApis,
                        onProviderSelected = { api ->
                            selectedApi = api
                            DesktopDataStore.updateSettings { it.copy(selectedProvider = api.name) }
                        },
                        onReload = { selectedApi?.let { loadHomeContent(it) } },
                        onRandom = { pickRandomItem() },
                        onSearch = { onNavigate(DesktopScreen.Search()) }
                    )
                }

                // ── HERO BANNER ────────────────────────────────────────────────
                if (featuredItems.isNotEmpty() && !isLoading) {
                    item {
                        Win11HeroBanner(
                            items = featuredItems,
                            onPlayClick = { item ->
                                onNavigate(DesktopScreen.Result(url = item.url, apiName = item.apiName, searchResponse = item, autoPlay = true))
                            },
                            onDetailsClick = { item ->
                                onNavigate(DesktopScreen.Result(url = item.url, apiName = item.apiName, searchResponse = item, autoPlay = false))
                            },
                            modifier = Modifier.padding(horizontal = 28.dp)
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                }

                // ── TYPE FILTER CHIPS ─────────────────────────────────────────
                item {
                    Win11TypeFilter(
                        selected = selectedType,
                        onSelect = { selectedType = it },
                        modifier = Modifier.padding(horizontal = 28.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                }

                // ── LOADING ────────────────────────────────────────────────────
                if (isLoading) {
                    item { Win11Shimmer() }
                } else if (errorMessage != null) {
                    item {
                        Win11ErrorState(
                            message = errorMessage!!,
                            onRetry = { selectedApi?.let { loadHomeContent(it) } },
                            mainUrl = selectedApi?.mainUrl
                        )
                    }
                } else {
                    // ── CONTINUE WATCHING ──────────────────────────────────────
                    if (watchHistory.isNotEmpty()) {
                        item {
                            Win11SectionHeader(title = "Continue Watching")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 28.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(watchHistory) { progress ->
                                    Win11ContinueCard(
                                        progress = progress,
                                        onClick = {
                                            onNavigate(DesktopScreen.Result(
                                                url = progress.url,
                                                apiName = progress.apiName,
                                                autoPlay = true
                                            ))
                                        }
                                    )
                                }
                            }
                            Spacer(Modifier.height(28.dp))
                        }
                    }

                    // ── CATALOGUE ROWS ─────────────────────────────────────────
                    items(filteredLists) { pageList ->
                        if (pageList.list.isNotEmpty()) {
                            Win11SectionHeader(title = pageList.name)
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 28.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                            Spacer(Modifier.height(28.dp))
                        }
                    }

                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────
@Composable
private fun Win11HomeHeader(
    selectedApi: MainAPI?,
    registeredApis: List<MainAPI>,
    onProviderSelected: (MainAPI) -> Unit,
    onReload: () -> Unit,
    onRandom: () -> Unit,
    onSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Title + provider name
        Column {
            Text(
                "Home",
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                color = TxtPrimary
            )
            if (selectedApi != null) {
                Text(
                    selectedApi.name,
                    fontSize = 12.sp,
                    color = TxtTertiary
                )
            }
        }

        // Right: Controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Provider picker
            var providerDropdown by remember { mutableStateOf(false) }
            Box {
                Win11SmallButton(
                    icon = Icons.Default.Source,
                    label = selectedApi?.name ?: "Provider",
                    onClick = { providerDropdown = true }
                )
                DropdownMenu(
                    expanded = providerDropdown,
                    onDismissRequest = { providerDropdown = false },
                    modifier = Modifier.background(BgCard).border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                ) {
                    registeredApis.forEach { api ->
                        val isSelected = api == selectedApi
                        DropdownMenuItem(
                            text = {
                                Text(
                                    api.name,
                                    color = if (isSelected) Accent else TxtPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            leadingIcon = if (isSelected) ({
                                Icon(Icons.Default.Check, null, tint = Accent, modifier = Modifier.size(13.dp))
                            }) else null,
                            onClick = { onProviderSelected(api); providerDropdown = false }
                        )
                    }
                }
            }

            // Icon buttons
            Win11IconButton(icon = Icons.Default.Refresh, tooltip = "Reload", onClick = onReload)
            Win11IconButton(icon = Icons.Default.Casino, tooltip = "Surprise me", tint = Accent, onClick = onRandom)
            Win11IconButton(icon = Icons.Default.Search, tooltip = "Search", onClick = onSearch)
        }
    }
}

// ─── Compact labelled button ──────────────────────────────────────────────────
@Composable
private fun Win11SmallButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = BgLayer,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.height(32.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, null, tint = TxtSecondary, modifier = Modifier.size(13.dp))
            Text(
                label,
                color = TxtSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp)
            )
            Icon(Icons.Default.KeyboardArrowDown, null, tint = TxtTertiary, modifier = Modifier.size(12.dp))
        }
    }
}

// ─── Icon-only button ─────────────────────────────────────────────────────────
@Composable
private fun Win11IconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tooltip: String,
    tint: Color = TxtSecondary,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (hovered) BgLayer else Color.Transparent,
        border = if (hovered) BorderStroke(1.dp, BorderSubtle) else null,
        modifier = Modifier.size(32.dp).hoverable(interactionSource)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, tooltip, tint = tint, modifier = Modifier.size(16.dp))
        }
    }
}

// ─── Hero Banner ──────────────────────────────────────────────────────────────
@Composable
private fun Win11HeroBanner(
    items: List<SearchResponse>,
    onPlayClick: (SearchResponse) -> Unit,
    onDetailsClick: (SearchResponse) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    var currentIndex by remember { mutableStateOf(0) }
    val current = items[currentIndex]

    LaunchedEffect(Unit) {
        while (true) {
            delay(7000)
            currentIndex = (currentIndex + 1) % items.size
        }
    }

    AnimatedContent(
        targetState = current,
        transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(300)) },
        label = "hero",
        modifier = modifier
    ) { item ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onDetailsClick(item) }
        ) {
            // Backdrop
            SubcomposeAsyncImage(
                model = item.posterUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                error = {
                    Box(modifier = Modifier.fillMaxSize().background(BgLayer))
                }
            )

            // Gradients
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color.Black.copy(0.92f), Color.Black.copy(0.65f), Color.Black.copy(0.35f)))
            ))
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.75f)))
            ))

            // Main Content Row (Poster Cover Card + Title & Info)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
                    .fillMaxWidth(0.85f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Vertical Poster Cover Image Card on Left
                Surface(
                    modifier = Modifier
                        .width(135.dp)
                        .height(200.dp)
                        .clickable { onDetailsClick(item) },
                    shape = RoundedCornerShape(10.dp),
                    color = BgCard,
                    border = BorderStroke(1.dp, Color.White.copy(0.3f))
                ) {
                    SubcomposeAsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = {
                            Box(
                                modifier = Modifier.fillMaxSize().background(BgLayer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Movie, null, tint = TxtTertiary, modifier = Modifier.size(36.dp))
                            }
                        }
                    )
                }

                Spacer(Modifier.width(20.dp))

                // 2. Title & Action Buttons Column
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Type pill
                    val typeLabel = when (item.type) {
                        TvType.Movie, TvType.AnimeMovie -> "MOVIE"
                        TvType.TvSeries -> "SERIES"
                        TvType.Anime -> "ANIME"
                        TvType.Live -> "LIVE TV"
                        else -> item.type?.name?.uppercase()
                    }
                    if (typeLabel != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Accent
                        ) {
                            Text(
                                typeLabel,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    Text(
                        item.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 26.sp
                    )

                    Spacer(Modifier.height(6.dp))

                    // Genre tags (Action • Drama / Crime • Mystery)
                    val genreText = remember(item) {
                        when (item.type) {
                            TvType.Movie, TvType.AnimeMovie -> "Action • Drama • Thriller"
                            TvType.TvSeries -> "Drama • Crime • Mystery"
                            TvType.Anime -> "Action • Animation • Fantasy"
                            TvType.Live -> "Live Stream • News • Entertainment"
                            else -> "Featured • Popular"
                        }
                    }
                    Text(
                        genreText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Accent
                    )

                    Spacer(Modifier.height(6.dp))

                    // Short Description
                    val shortDescription = remember(item) {
                        "Discover '${item.name}' streaming live via ${item.apiName}. High definition playback with multi-language subtitle tracks and full episode selection."
                    }
                    Text(
                        shortDescription,
                        fontSize = 12.sp,
                        color = Color.White.copy(0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )

                    Spacer(Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Play button -> Immediately plays the video
                        Surface(
                            onClick = { onPlayClick(item) },
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White,
                            modifier = Modifier.height(34.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(17.dp))
                                Text("Play Stream", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                        // Details button -> Goes to Details/Result page
                        Surface(
                            onClick = { onDetailsClick(item) },
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White.copy(0.12f),
                            border = BorderStroke(1.dp, Color.White.copy(0.3f)),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Default.Info, null, tint = Color.White, modifier = Modifier.size(15.dp))
                                Text("Details", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            // Dot indicators
            Row(
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.take(8).forEachIndexed { i, _ ->
                    val isActive = i == currentIndex
                    Box(
                        modifier = Modifier
                            .animateContentSize()
                            .size(if (isActive) 18.dp else 5.dp, 5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isActive) Accent else Color.White.copy(0.35f))
                            .clickable { currentIndex = i }
                    )
                }
            }
        }
    }
}

// ─── Type Filter Chips ────────────────────────────────────────────────────────
@Composable
private fun Win11TypeFilter(
    selected: TvType?,
    onSelect: (TvType?) -> Unit,
    modifier: Modifier = Modifier
) {
    val types = listOf(
        null to "All",
        TvType.Movie to "Movies",
        TvType.TvSeries to "Series",
        TvType.Anime to "Anime",
        TvType.Cartoon to "Cartoon",
        TvType.Live to "Live TV",
        TvType.Documentary to "Docs",
    )

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        types.forEach { (type, label) ->
            val isSelected = selected == type
            val textColor by animateColorAsState(
                if (isSelected) Color.White else TxtTertiary, tween(150)
            )
            val accentColor by animateColorAsState(
                if (isSelected) Accent else Color.Transparent, tween(150)
            )

            Surface(
                onClick = { onSelect(type) },
                shape = RoundedCornerShape(5.dp),
                color = if (isSelected) Accent.copy(0.12f) else Color.Transparent,
                border = BorderStroke(1.dp, if (isSelected) Accent.copy(0.5f) else BorderSubtle)
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
}

// ─── Section Header ───────────────────────────────────────────────────────────
@Composable
private fun Win11SectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = 10.dp),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = TxtPrimary
    )
}

// ─── Continue Watching Card ───────────────────────────────────────────────────
@Composable
private fun Win11ContinueCard(
    progress: WatchProgress,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.02f else 1f, spring(stiffness = Spring.StiffnessMediumLow))

    Box(modifier = Modifier.scale(scale)) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            color = BgCard,
            border = BorderStroke(1.dp, if (hovered) BorderDef else BorderSubtle),
            modifier = Modifier.width(210.dp).height(118.dp).hoverable(interactionSource)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!progress.posterUrl.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = progress.posterUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = { Box(Modifier.fillMaxSize().background(BgLayer)) }
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(BgLayer))
                }

                // Overlay
                Box(modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(0.2f),
                        0.5f to Color.Black.copy(0.55f),
                        1f to Color.Black.copy(0.92f)
                    )
                ))

                Column(
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Title
                    Text(
                        progress.title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 15.sp
                    )
                    // Progress
                    Column {
                        val pct = if (progress.durationMillis > 0)
                            (progress.positionMillis.toFloat() / progress.durationMillis).coerceIn(0f, 1f) else 0f
                        LinearProgressIndicator(
                            progress = { pct },
                            modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)),
                            color = Accent,
                            trackColor = Color.White.copy(0.2f)
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${(pct * 100).toInt()}% watched",
                            color = Color.White.copy(0.5f),
                            fontSize = 10.sp
                        )
                    }
                }

                // Hover play icon
                if (hovered) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.Black.copy(0.55f),
                            border = BorderStroke(1.dp, Color.White.copy(0.3f)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Shimmer Loading ──────────────────────────────────────────────────────────
@Composable
private fun Win11Shimmer() {
    val alpha by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0.06f, targetValue = 0.14f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "shimmerAlpha"
    )
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
        // Hero
        Box(modifier = Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha)))
        Spacer(Modifier.height(24.dp))
        // Row label
        Box(modifier = Modifier.width(120.dp).height(14.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha)))
        Spacer(Modifier.height(10.dp))
        // Cards row
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(6) {
                Box(modifier = Modifier.width(135.dp).height(195.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha)))
            }
        }
    }
}

// ─── Error State ──────────────────────────────────────────────────────────────
@Composable
private fun Win11ErrorState(
    message: String,
    onRetry: () -> Unit,
    mainUrl: String?
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(280.dp).padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFCF6679), modifier = Modifier.size(36.dp))
            Text(message, color = TxtSecondary, fontSize = 13.sp, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = onRetry,
                    shape = RoundedCornerShape(6.dp),
                    color = BgLayer,
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, null, tint = TxtSecondary, modifier = Modifier.size(14.dp))
                        Text("Retry", color = TxtSecondary, fontSize = 12.sp)
                    }
                }
                if (mainUrl != null) {
                    Surface(
                        onClick = { try { Desktop.getDesktop().browse(URI(mainUrl)) } catch (_: Exception) {} },
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Public, null, tint = TxtTertiary, modifier = Modifier.size(13.dp))
                            Text("Open in Browser", color = TxtTertiary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ─── No Providers State ───────────────────────────────────────────────────────
@Composable
private fun Win11NoProviders(onNavigate: (DesktopScreen) -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            Icon(Icons.Default.Extension, null, tint = TxtTertiary, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(4.dp))
            Text("No Providers Installed", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = TxtPrimary)
            Text(
                "Install a plugin extension to browse content.",
                color = TxtTertiary, fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                onClick = { onNavigate(DesktopScreen.Plugins) },
                shape = RoundedCornerShape(6.dp),
                color = Accent
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Extension, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Text("Browse Extensions", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}
