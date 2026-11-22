package com.lagradost.cloudstream.desktop.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import com.lagradost.cloudstream.desktop.storage.WatchProgress
import com.lagradost.cloudstream.desktop.ui.theme.*
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import kotlinx.coroutines.delay
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import java.util.Locale

// ─── Reusable Horizontal Mouse Scroll Extensions ──────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.onHorizontalMouseScroll(
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    scrollFactor: Float = 45f
): Modifier = this.pointerInput(lazyListState) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Scroll) {
                val change = event.changes.firstOrNull()
                if (change != null && !change.isConsumed) {
                    val delta = if (change.scrollDelta.y != 0f) change.scrollDelta.y else change.scrollDelta.x
                    if (delta != 0f) {
                        val scrollAmount = delta * scrollFactor
                        val canScroll = if (scrollAmount > 0f) lazyListState.canScrollForward else lazyListState.canScrollBackward
                        if (canScroll) {
                            lazyListState.dispatchRawDelta(scrollAmount)
                            change.consume()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.onHorizontalMouseScroll(
    scrollState: ScrollState,
    scrollFactor: Float = 45f
): Modifier = this.pointerInput(scrollState) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Scroll) {
                val change = event.changes.firstOrNull()
                if (change != null && !change.isConsumed) {
                    val delta = if (change.scrollDelta.y != 0f) change.scrollDelta.y else change.scrollDelta.x
                    if (delta != 0f) {
                        val scrollAmount = delta * scrollFactor
                        val canScroll = if (scrollAmount > 0f) scrollState.canScrollForward else scrollState.canScrollBackward
                        if (canScroll) {
                            scrollState.dispatchRawDelta(scrollAmount)
                            change.consume()
                        }
                    }
                }
            }
        }
    }
}

// ─── Shared Media Card (poster + title) ──────────────────────────────────────

@Composable
fun MediaCard(
    item: SearchResponse,
    onClick: () -> Unit,
    width: Dp = 140.dp,
    height: Dp = 205.dp,
    showType: Boolean = true,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val isHighlighted = hovered || focused
    val scale by animateFloatAsState(
        targetValue = if (isHighlighted) 1.03f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    val displayTitle = remember(item.name, item.url) {
        var title = item.name.trim()
        val genreRegex = Regex("""(?i)\b(Drama|Action|Comedy|Crime|Mystery|Romance|Thriller|Horror|Sci-Fi|Fantasy|Family|Animation|Documentary)\b(?:\s*,\s*|$)""")
        if (title.isBlank() || (title.contains(",") && genreRegex.containsMatchIn(title))) {
            val slug = item.url.substringAfterLast("/").substringBefore("?").substringBefore("#")
            if (slug.isNotBlank()) {
                title = slug.replace(Regex("""^\d+-"""), "").replace("-", " ")
                    .split(" ")
                    .joinToString(" ") { word ->
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    }
            }
        }

        title = title
            .replace(Regex("""(?i)\[?(Dual\s*Audio|Hindi|English|Bengali|Multi|Sub|Dubbed|Dub)\]?"""), "")
            .replace(Regex("""(?i)\b(4K|2160p|1080p|720p|480p|360p|WEBRip|WebRip|HDTS|BluRay|WEB-DL|AMZN|NF|DSNP|HDTV|x264|x265|AAC|HEVC)\b"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

        title.ifBlank { item.name }
    }

    Column(
        modifier = modifier
            .width(width)
            .scale(scale)
            .hoverable(interactionSource)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .width(width)
                .height(height),
            shape = RoundedCornerShape(8.dp),
            color = SurfaceCard,
            border = BorderStroke(
                width = if (isHighlighted) 2.dp else 1.dp,
                color = if (focused) Accent else if (hovered) BorderDefault else BorderSubtle
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Poster image
                SubcomposeAsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize().background(SurfaceLayer),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Accent,
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier.fillMaxSize().background(SurfaceLayer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.name.take(2).uppercase(),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = TextTertiary
                            )
                        }
                    }
                )

                // Top-Left Quality Badge (HD, 4K, 1080p)
                TopLeftQualityBadge(
                    item = item,
                    modifier = Modifier.align(Alignment.TopStart)
                )

                // Top-Right Audio/Dub/Sub/Episode Badges
                TopRightAudioBadge(
                    item = item,
                    modifier = Modifier.align(Alignment.TopEnd)
                )

                // Hover play overlay
                if (hovered) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.Black.copy(0.55f),
                            border = BorderStroke(1.dp, Color.White.copy(0.3f)),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = displayTitle,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                lineHeight = 15.sp
            ),
            color = TextPrimary,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ─── Hero Banner ─────────────────────────────────────────────────────────────

@Composable
fun HeroBanner(
    items: List<SearchResponse>,
    onItemClick: (SearchResponse) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    var currentIndex by remember { mutableStateOf(0) }
    val current = items[currentIndex]

    // Auto-cycle every 6 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(6000)
            currentIndex = (currentIndex + 1) % items.size
        }
    }

    AnimatedContent(
        targetState = current,
        transitionSpec = { fadeIn(tween(800)) togetherWith fadeOut(tween(400)) },
        label = "heroBanner",
        modifier = modifier
    ) { item ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { onItemClick(item) }
        ) {
            // Backdrop blurred background
            SubcomposeAsyncImage(
                model = item.posterUrl,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                error = {
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.linearGradient(
                                listOf(SurfaceLayer, SurfaceCard)
                            )
                        )
                    )
                }
            )

            // Dark gradient overlay left-to-right
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = 0.92f), Color.Black.copy(alpha = 0.65f), Color.Black.copy(alpha = 0.35f))
                    )
                )
            )

            // Bottom fade
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                    )
                )
            )

            // Main Content Row (Poster Cover Card + Title & Actions)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
                    .fillMaxWidth(0.85f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Poster Cover Image Card
                Surface(
                    modifier = Modifier
                        .width(135.dp)
                        .height(200.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceCard,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                ) {
                    SubcomposeAsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = {
                            Box(
                                modifier = Modifier.fillMaxSize().background(SurfaceLayer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Movie, null, tint = TextTertiary, modifier = Modifier.size(36.dp))
                            }
                        }
                    )
                }

                Spacer(Modifier.width(20.dp))

                // 2. Title & Details Column
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    val typeLabel = when (item.type) {
                        TvType.Movie, TvType.AnimeMovie -> "MOVIE"
                        TvType.TvSeries -> "TV SERIES"
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
                                text = typeLabel,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnAccent
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onItemClick(item) },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = OnAccent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Play Stream", color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = { onItemClick(item) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White.copy(0.4f)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Details", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // Pagination dots
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items.take(8).forEachIndexed { i, _ ->
                    Box(
                        modifier = Modifier
                            .size(if (i == currentIndex) 20.dp else 6.dp, 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (i == currentIndex) MediaAccent
                                else Color.White.copy(alpha = 0.4f)
                            )
                            .clickable { currentIndex = i }
                    )
                }
            }
        }
    }
}

// ─── Continue Watching Card ───────────────────────────────────────────────────

@Composable
fun ContinueWatchingCard(
    progress: WatchProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(220.dp)
            .height(130.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Poster Background Image
            if (!progress.posterUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = progress.posterUrl,
                    contentDescription = progress.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = {
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.linearGradient(listOf(SurfaceCard, SurfaceBase))
                            )
                        )
                    }
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(SurfaceCard, SurfaceBase))
                    )
                )
            }

            // Dark gradient overlay
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.67f), Color.Black.copy(alpha = 0.90f))
                    )
                )
            )

            // Content
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = MediaAccent,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = progress.title,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column {
                    val pct = if (progress.durationMillis > 0)
                        (progress.positionMillis.toFloat() / progress.durationMillis).coerceIn(0f, 1f)
                    else 0f
                    LinearProgressIndicator(
                        progress = { pct },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = MediaAccent,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${(pct * 100).toInt()}% watched",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.LightGray
                    )
                }
            }
        }
    }
}

// ─── TvType Filter Chips ─────────────────────────────────────────────────────

@Composable
fun TvTypeChips(
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
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        types.forEach { (type, label) ->
            val isSelected = selected == type
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(type) },
                label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Accent,
                    selectedLabelColor = OnAccent,
                    containerColor = SurfaceCard,
                    labelColor = TextSecondary
                )
            )
        }
    }
}

// ─── Home Shimmer Loading Placeholder ───────────────────────────────────────

@Composable
fun HomeLoadingShimmer(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        // Hero placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(TextPrimary.copy(alpha = alpha))
        )
        Spacer(Modifier.height(28.dp))
        // Row 1 placeholder
        Box(
            modifier = Modifier
                .width(140.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(TextPrimary.copy(alpha = alpha))
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(6) {
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(225.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TextPrimary.copy(alpha = alpha))
                )
            }
        }
    }
}

// ─── Poster Badges (Top-Left Quality & Top-Right Audio/Dub/Sub overlay) ──────

@Composable
fun TopLeftQualityBadge(item: SearchResponse, modifier: Modifier = Modifier) {
    val qualityText = remember(item) {
        val raw = item.quality?.name ?: run {
            val name = item.name
            when {
                name.contains("4K", ignoreCase = true) || name.contains("2160p", ignoreCase = true) -> "4K"
                name.contains("1080p", ignoreCase = true) -> "1080p"
                name.contains("720p", ignoreCase = true) -> "720p"
                name.contains("CAM", ignoreCase = true) || name.contains("HDTS", ignoreCase = true) -> "CAM"
                item.type == TvType.Movie || item.type == TvType.AnimeMovie -> "HD"
                else -> null
            }
        }
        when (raw?.uppercase()) {
            "FOURK", "4K", "UHD", "2160P" -> "4K"
            "FHD", "1080P" -> "1080p"
            "HD", "720P" -> "HD"
            "SD", "480P", "360P" -> "SD"
            else -> raw
        }
    }

    if (qualityText != null) {
        val (bg, textColor) = when (qualityText.uppercase()) {
            "4K" -> Pair(Color(0xFFD97706), Color.Black)
            "1080P" -> Pair(Color(0xFF0078D4), Color.White)
            "720P" -> Pair(Color(0xFF107C41), Color.White)
            else -> Pair(Color.Black.copy(alpha = 0.75f), Color.White)
        }
        Box(
            modifier = modifier
                .padding(6.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(bg)
                .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = qualityText,
                fontSize = 9.5.sp,
                lineHeight = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun TopRightAudioBadge(item: SearchResponse, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(6.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        when (item) {
            is com.lagradost.cloudstream3.AnimeSearchResponse -> {
                val dubEp = item.episodes.get(com.lagradost.cloudstream3.DubStatus.Dubbed)
                val subEp = item.episodes.get(com.lagradost.cloudstream3.DubStatus.Subbed)
                val hasDub = item.dubStatus?.contains(com.lagradost.cloudstream3.DubStatus.Dubbed) == true || (dubEp != null && dubEp > 0)
                val hasSub = item.dubStatus?.contains(com.lagradost.cloudstream3.DubStatus.Subbed) == true || (subEp != null && subEp > 0)

                if (hasDub) {
                    val label = if (dubEp != null && dubEp > 0) "DUB $dubEp" else "DUB"
                    Box(
                        modifier = Modifier
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                            .padding(horizontal = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 9.5.sp,
                            lineHeight = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                if (hasSub) {
                    val label = if (subEp != null && subEp > 0) "SUB $subEp" else "SUB"
                    Box(
                        modifier = Modifier
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(0.75f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 9.5.sp,
                            lineHeight = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            is com.lagradost.cloudstream3.TvSeriesSearchResponse -> {
                val ep = item.episodes
                val isDub = item.name.contains("Dub", ignoreCase = true) || item.name.contains("Dual", ignoreCase = true)
                if (isDub) {
                    Box(
                        modifier = Modifier
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                            .padding(horizontal = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "DUB",
                            fontSize = 9.5.sp,
                            lineHeight = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                if (ep != null && ep > 0) {
                    Box(
                        modifier = Modifier
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(0.75f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "EP $ep",
                            fontSize = 9.5.sp,
                            lineHeight = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            else -> {
                val name = item.name
                val isDub = name.contains("Dub", ignoreCase = true) ||
                        name.contains("Dual", ignoreCase = true) ||
                        name.contains("Hindi", ignoreCase = true) ||
                        name.contains("Bengali", ignoreCase = true)
                if (isDub) {
                    Box(
                        modifier = Modifier
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                            .padding(horizontal = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "DUB",
                            fontSize = 9.5.sp,
                            lineHeight = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
