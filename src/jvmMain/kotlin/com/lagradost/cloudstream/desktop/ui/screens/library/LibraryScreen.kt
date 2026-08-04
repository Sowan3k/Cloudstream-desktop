package com.lagradost.cloudstream.desktop.ui.screens.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.lagradost.cloudstream.desktop.storage.DesktopDataStore
import com.lagradost.cloudstream.desktop.storage.WatchProgress
import com.lagradost.cloudstream.desktop.storage.WatchlistItem
import com.lagradost.cloudstream.desktop.ui.navigation.DesktopScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Windows 11 color tokens ─────────────────────────────────────────────────
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
private val AccentHover    = Color(0xFF1084D8)

// ─── Sort modes ───────────────────────────────────────────────────────────────
enum class LibrarySortMode(val label: String) {
    RECENT("Recently Watched"),
    ALPHABETICAL_A("Name: A → Z"),
    ALPHABETICAL_Z("Name: Z → A"),
    PROGRESS_HIGH("Most Watched"),
    PROGRESS_LOW("Least Watched"),
}

// ─── Main Screen ──────────────────────────────────────────────────────────────
@Composable
fun LibraryScreen(
    onNavigate: (DesktopScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    var refreshKey by remember { mutableStateOf(0) }
    val settings = remember(refreshKey) { DesktopDataStore.currentSettings }
    val watchHistory = settings.watchHistory
    val watchlist = settings.watchlist

    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(LibrarySortMode.RECENT) }
    var showSortMenu by remember { mutableStateOf(false) }

    val continueCount = watchHistory.count { it.positionMillis > 0 && it.durationMillis > 0 }
    val historyCount  = watchHistory.size
    val watchlistCount = watchlist.size

    val currentCount = when (selectedTab) {
        0 -> continueCount
        1 -> historyCount
        else -> watchlistCount
    }

    Column(
        modifier = modifier.fillMaxSize().background(SurfaceBase)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Library",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "$currentCount item${if (currentCount != 1) "s" else ""}",
                    fontSize = 13.sp,
                    color = TextTertiary
                )
            }

            // Sort dropdown
            Box {
                Surface(
                    onClick = { showSortMenu = true },
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
                        Icon(Icons.Default.Sort, null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                        Text(sortMode.label, color = TextSecondary, fontSize = 12.sp)
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                    }
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    modifier = Modifier.background(Color(0xFF2C2C2C)).border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                ) {
                    LibrarySortMode.entries.forEach { mode ->
                        val isSelected = sortMode == mode
                        DropdownMenuItem(
                            text = {
                                Text(
                                    mode.label,
                                    color = if (isSelected) AccentBlue else TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            leadingIcon = if (isSelected) ({
                                Icon(Icons.Default.Check, null, tint = AccentBlue, modifier = Modifier.size(14.dp))
                            }) else null,
                            onClick = { sortMode = mode; showSortMenu = false },
                            modifier = Modifier.background(Color.Transparent)
                        )
                    }
                    if (selectedTab <= 1) {
                        HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = { Text("Clear All History", color = Color(0xFFCF6679), fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.DeleteSweep, null, tint = Color(0xFFCF6679), modifier = Modifier.size(14.dp)) },
                            onClick = {
                                DesktopDataStore.clearHistory()
                                refreshKey++
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
        }

        // ── Search ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = SurfaceLayer,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.weight(1f).height(36.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Search, null, tint = TextTertiary, modifier = Modifier.size(15.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 13.sp),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text("Search library...", color = TextTertiary, fontSize = 13.sp)
                                }
                                innerTextField()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            Icons.Default.Close, null,
                            tint = TextTertiary,
                            modifier = Modifier.size(14.dp).clickable { searchQuery = "" }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Tabs ─────────────────────────────────────────────────────────────
        Win11Tabs(
            tabs = listOf(
                "Continue Watching" to continueCount,
                "All History" to historyCount,
                "Watchlist" to watchlistCount
            ),
            selected = selectedTab,
            onSelect = { selectedTab = it }
        )

        HorizontalDivider(color = BorderSubtle)

        // ── Content ──────────────────────────────────────────────────────────
        when (selectedTab) {
            0 -> {
                val items = watchHistory
                    .filter { it.positionMillis > 0 && it.durationMillis > 0 }
                    .filter { searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) }
                    .applySortMode(sortMode)
                PosterGrid(isEmpty = items.isEmpty(), emptyIcon = Icons.Default.PlayCircleOutline, emptyMessage = "Nothing in progress yet") {
                    items(items, key = { it.url }) { progress ->
                        HistoryCard(progress = progress, showProgress = true,
                            onClick = { onNavigate(DesktopScreen.Result(url = progress.url, apiName = progress.apiName, autoPlay = true)) },
                            onRemove = { DesktopDataStore.removeFromHistory(progress.url); refreshKey++ }
                        )
                    }
                }
            }
            1 -> {
                val items = watchHistory
                    .filter { searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) }
                    .applySortMode(sortMode)
                PosterGrid(isEmpty = items.isEmpty(), emptyIcon = Icons.Default.History, emptyMessage = "No watch history yet") {
                    items(items, key = { it.url }) { progress ->
                        HistoryCard(progress = progress, showProgress = false,
                            onClick = { onNavigate(DesktopScreen.Result(url = progress.url, apiName = progress.apiName)) },
                            onRemove = { DesktopDataStore.removeFromHistory(progress.url); refreshKey++ }
                        )
                    }
                }
            }
            2 -> {
                val items = watchlist
                    .filter { searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) }
                    .sortedWith(when (sortMode) {
                        LibrarySortMode.ALPHABETICAL_A -> compareBy { it.title.lowercase() }
                        LibrarySortMode.ALPHABETICAL_Z -> compareByDescending { it.title.lowercase() }
                        else -> compareByDescending { it.addedTimestamp }
                    })
                PosterGrid(isEmpty = items.isEmpty(), emptyIcon = Icons.Default.BookmarkBorder, emptyMessage = "Your watchlist is empty") {
                    items(items, key = { it.url }) { item ->
                        WatchlistCard(item = item,
                            onClick = { onNavigate(DesktopScreen.Result(url = item.url, apiName = item.apiName)) },
                            onRemove = { DesktopDataStore.removeFromWatchlist(item.url); refreshKey++ }
                        )
                    }
                }
            }
        }
    }
}

// ─── Windows 11 Tabs ─────────────────────────────────────────────────────────
@Composable
private fun Win11Tabs(
    tabs: List<Pair<String, Int>>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier.padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        tabs.forEachIndexed { index, (label, count) ->
            val isSelected = index == selected
            val textColor by animateColorAsState(
                if (isSelected) TextPrimary else TextTertiary,
                animationSpec = tween(150)
            )
            val underlineColor by animateColorAsState(
                if (isSelected) AccentBlue else Color.Transparent,
                animationSpec = tween(200)
            )

            Row(
                modifier = Modifier
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onSelect(index) }
                    .drawBehind {
                        // Draw underline at the bottom without affecting layout
                        drawRect(
                            color = underlineColor,
                            topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 2.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx())
                        )
                    }
                    .padding(end = 32.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    label,
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
                if (count > 0) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isSelected) AccentBlue.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.06f)
                    ) {
                        Text(
                            count.toString(),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            fontSize = 11.sp,
                            color = if (isSelected) AccentBlue else TextTertiary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ─── Poster Grid ─────────────────────────────────────────────────────────────
@Composable
private fun PosterGrid(
    isEmpty: Boolean,
    emptyIcon: ImageVector,
    emptyMessage: String,
    content: LazyGridScope.() -> Unit
) {
    if (isEmpty) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(emptyIcon, null, tint = TextTertiary, modifier = Modifier.size(40.dp))
                Text(emptyMessage, color = TextSecondary, fontSize = 14.sp)
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 145.dp),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
            content = content
        )
    }
}

// ─── History Card ─────────────────────────────────────────────────────────────
@Composable
private fun HistoryCard(
    progress: WatchProgress,
    showProgress: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    var showMenu by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.02f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )
    val pct = if (progress.durationMillis > 0)
        (progress.positionMillis.toFloat() / progress.durationMillis).coerceIn(0f, 1f)
    else 0f

    Box(modifier = Modifier.scale(scale)) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, if (hovered) BorderDefault else BorderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .hoverable(interactionSource)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Poster
                if (!progress.posterUrl.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = progress.posterUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = { Box(Modifier.fillMaxSize().background(SurfaceLayer)) },
                        error = { PosterPlaceholder(progress.title) }
                    )
                } else {
                    PosterPlaceholder(progress.title)
                }

                // Bottom gradient + info
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.3f to Color.Black.copy(0.5f),
                                1f to Color.Black.copy(0.95f)
                            )
                        )
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Column {
                        // Episode badge
                        if (progress.seasonIndex != null || progress.episodeIndex != null) {
                            val badge = buildString {
                                if (progress.seasonIndex != null) append("S${progress.seasonIndex}")
                                if (progress.episodeIndex != null) append("E${progress.episodeIndex}")
                            }
                            Text(
                                badge,
                                color = Color.White.copy(0.7f),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                        Text(
                            progress.title.substringBefore(" — ").trim(),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                            lineHeight = 15.sp
                        )
                        if (showProgress && progress.durationMillis > 0) {
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { pct },
                                modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)),
                                color = AccentBlue,
                                trackColor = Color.White.copy(0.2f)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "${(pct * 100).toInt()}%  •  ${formatTime(progress.positionMillis)}",
                                color = Color.White.copy(0.55f),
                                fontSize = 10.sp
                            )
                        } else if (!showProgress) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                formatRelativeTime(progress.lastWatchedTimestamp),
                                color = Color.White.copy(0.45f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Hover overlay: play icon
                if (hovered) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.Black.copy(0.55f),
                            border = BorderStroke(1.dp, Color.White.copy(0.3f)),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }

                // Context menu button
                if (hovered || showMenu) {
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                        Surface(
                            onClick = { showMenu = true },
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(0.55f),
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MoreVert, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(SurfaceCard).border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open details", color = TextPrimary, fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.OpenInNew, null, tint = TextSecondary, modifier = Modifier.size(14.dp)) },
                                onClick = { showMenu = false; onClick() }
                            )
                            DropdownMenuItem(
                                text = { Text("Remove", color = Color(0xFFCF6679), fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFCF6679), modifier = Modifier.size(14.dp)) },
                                onClick = { showMenu = false; onRemove() }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Watchlist Card ───────────────────────────────────────────────────────────
@Composable
private fun WatchlistCard(
    item: WatchlistItem,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    var showMenu by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.02f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    Box(modifier = Modifier.scale(scale)) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, if (hovered) BorderDefault else BorderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .hoverable(interactionSource)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!item.posterUrl.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = item.posterUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = { Box(Modifier.fillMaxSize().background(SurfaceLayer)) },
                        error = { PosterPlaceholder(item.title) }
                    )
                } else {
                    PosterPlaceholder(item.title)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.3f to Color.Black.copy(0.5f),
                                1f to Color.Black.copy(0.95f)
                            )
                        )
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            item.title,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                            lineHeight = 15.sp
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            formatRelativeTime(item.addedTimestamp),
                            color = Color.White.copy(0.45f),
                            fontSize = 10.sp
                        )
                    }
                }

                if (hovered) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.Black.copy(0.55f),
                            border = BorderStroke(1.dp, Color.White.copy(0.3f)),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }

                if (hovered || showMenu) {
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                        Surface(
                            onClick = { showMenu = true },
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(0.55f),
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MoreVert, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(SurfaceCard).border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open details", color = TextPrimary, fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.OpenInNew, null, tint = TextSecondary, modifier = Modifier.size(14.dp)) },
                                onClick = { showMenu = false; onClick() }
                            )
                            DropdownMenuItem(
                                text = { Text("Remove", color = Color(0xFFCF6679), fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.BookmarkRemove, null, tint = Color(0xFFCF6679), modifier = Modifier.size(14.dp)) },
                                onClick = { showMenu = false; onRemove() }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Poster Placeholder ───────────────────────────────────────────────────────
@Composable
private fun PosterPlaceholder(title: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(SurfaceLayer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            title.take(2).uppercase(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextTertiary
        )
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────
private fun List<WatchProgress>.applySortMode(mode: LibrarySortMode): List<WatchProgress> = when (mode) {
    LibrarySortMode.RECENT        -> this
    LibrarySortMode.ALPHABETICAL_A -> sortedBy { it.title.lowercase() }
    LibrarySortMode.ALPHABETICAL_Z -> sortedByDescending { it.title.lowercase() }
    LibrarySortMode.PROGRESS_HIGH  -> sortedByDescending { if (it.durationMillis > 0) it.positionMillis.toFloat() / it.durationMillis else 0f }
    LibrarySortMode.PROGRESS_LOW   -> sortedBy { if (it.durationMillis > 0) it.positionMillis.toFloat() / it.durationMillis else 0f }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000) % 60; val m = (ms / 60000) % 60; val h = ms / 3600000
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatRelativeTime(ts: Long): String {
    val d = System.currentTimeMillis() - ts
    return when {
        d < 60_000       -> "Just now"
        d < 3_600_000    -> "${d / 60_000}m ago"
        d < 86_400_000   -> "${d / 3_600_000}h ago"
        d < 604_800_000  -> "${d / 86_400_000}d ago"
        else             -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
    }
}
