package com.lagradost.cloudstream.desktop.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import kotlinx.coroutines.delay
import java.util.Locale

// ─── Shared Media Card (poster + title) ──────────────────────────────────────

@Composable
fun MediaCard(
    item: SearchResponse,
    onClick: () -> Unit,
    width: Dp = 150.dp,
    height: Dp = 225.dp,
    showType: Boolean = true,
    modifier: Modifier = Modifier
) {
    var hovered by remember { mutableStateOf(false) }

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

        // Clean technical resolution and audio tags from poster title
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
            .clickable { onClick() }
    ) {
        Card(
            modifier = Modifier
                .width(width)
                .height(height),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (hovered) 12.dp else 3.dp
            ),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2A))
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
                            modifier = Modifier.fillMaxSize().background(Color(0xFF2A2A3A)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.verticalGradient(listOf(Color(0xFF2A2A3A), Color(0xFF1A1A2A)))
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = item.name.take(2).uppercase(),
                                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                )

                // Top-Left Quality Badge (HD, 4K, 1080p)
                TopLeftQualityBadge(
                    item = item,
                    modifier = Modifier.align(Alignment.TopStart)
                )

                // Top-Right Audio/Dub/Sub/Episode Badges (Dub, Sub)
                TopRightAudioBadge(
                    item = item,
                    modifier = Modifier.align(Alignment.TopEnd)
                )

                // Hover play overlay
                if (hovered) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Title text positioned below poster image outside the Card (matching screenshot)
        Text(
            text = displayTitle,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                lineHeight = 15.sp
            ),
            color = Color.White,
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
                .height(380.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable { onItemClick(item) }
        ) {
            // Backdrop
            SubcomposeAsyncImage(
                model = item.posterUrl,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                error = {
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1A1A3E), Color(0xFF2D1B69))
                            )
                        )
                    )
                }
            )

            // Gradient overlay left-to-right
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = 0.9f), Color.Transparent, Color.Black.copy(alpha = 0.3f))
                    )
                )
            )

            // Bottom fade
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
            )

            // Content
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(28.dp)
                    .fillMaxWidth(0.55f)
            ) {
                val typeLabel = when (item.type) {
                    TvType.Movie, TvType.AnimeMovie -> "MOVIE"
                    TvType.TvSeries -> "TV SERIES"
                    TvType.Anime -> "ANIME"
                    TvType.Live -> "LIVE"
                    else -> item.type?.name?.uppercase()
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (typeLabel != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = typeLabel,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                Text(
                    text = item.name,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onItemClick(item) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play Now", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { onItemClick(item) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Details")
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
                                if (i == currentIndex) MaterialTheme.colorScheme.primary
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2A))
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
                                Brush.linearGradient(listOf(Color(0xFF1A3A5C), Color(0xFF0D1B2A)))
                            )
                        )
                    }
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(Color(0xFF1A3A5C), Color(0xFF0D1B2A)))
                    )
                )
            }

            // Dark gradient overlay
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.90f))
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
                        tint = MaterialTheme.colorScheme.primary,
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
                        color = MaterialTheme.colorScheme.primary,
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
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.White,
                    containerColor = Color(0xFF2A2A3A),
                    labelColor = Color.LightGray
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
                .background(Color.White.copy(alpha = alpha))
        )
        Spacer(Modifier.height(28.dp))
        // Row 1 placeholder
        Box(
            modifier = Modifier
                .width(140.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = alpha))
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(6) {
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(225.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = alpha))
                )
            }
        }
    }
}

// ─── Poster Badges (Top-Left Quality & Top-Right Audio/Dub/Sub overlay) ──────

@Composable
fun TopLeftQualityBadge(item: SearchResponse, modifier: Modifier = Modifier) {
    val qualityText = remember(item) {
        val q = item.quality?.name
        if (q != null) q
        else {
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
    }

    if (qualityText != null) {
        Box(
            modifier = modifier
                .padding(4.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.Black.copy(alpha = 0.72f))
                .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(
                text = qualityText,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun TopRightAudioBadge(item: SearchResponse, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(4.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        when (item) {
            is com.lagradost.cloudstream3.AnimeSearchResponse -> {
                val dubEp = item.episodes.get(com.lagradost.cloudstream3.DubStatus.Dubbed)
                val subEp = item.episodes.get(com.lagradost.cloudstream3.DubStatus.Subbed)
                val hasDub = item.dubStatus?.contains(com.lagradost.cloudstream3.DubStatus.Dubbed) == true || (dubEp != null && dubEp > 0)
                val hasSub = item.dubStatus?.contains(com.lagradost.cloudstream3.DubStatus.Subbed) == true || (subEp != null && subEp > 0)

                if (hasDub) {
                    val label = if (dubEp != null && dubEp > 0) "Dub Ep $dubEp" else "Dub"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black
                        )
                    }
                }

                if (hasSub) {
                    val label = if (subEp != null && subEp > 0) "Sub Ep $subEp" else "Sub"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xDD1B2230))
                            .border(0.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
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
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "Dub",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black
                        )
                    }
                }
                if (ep != null && ep > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xDD1B2230))
                            .border(0.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "Ep $ep",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
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
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "Dub",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}
