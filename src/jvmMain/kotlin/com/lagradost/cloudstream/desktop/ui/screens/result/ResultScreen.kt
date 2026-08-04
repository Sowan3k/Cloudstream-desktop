package com.lagradost.cloudstream.desktop.ui.screens.result

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.SubcomposeAsyncImage
import com.lagradost.cloudstream.desktop.plugins.DesktopPluginLoader
import com.lagradost.cloudstream.desktop.ui.components.MediaCard
import com.lagradost.cloudstream.desktop.ui.navigation.DesktopScreen
import com.lagradost.cloudstream.desktop.ui.navigation.SubtitleTrackInfo
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ResultScreen(
    url: String,
    apiName: String,
    searchResponse: SearchResponse? = null,
    onNavigate: (DesktopScreen) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    var loadResponse by remember { mutableStateOf<LoadResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isExtractingLinks by remember { mutableStateOf(false) }
    var extractedLinks by remember { mutableStateOf<List<ExtractorLink>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<SubtitleTrackInfo>>(emptyList()) }
    var showQualityPicker by remember { mutableStateOf(false) }
    var selectedSeason by remember { mutableStateOf<Int?>(null) }
    var extractingEpisode by remember { mutableStateOf<Episode?>(null) }

    // Load media details
    LaunchedEffect(url, apiName) {
        isLoading = true
        errorMessage = null
        try {
            val api = DesktopPluginLoader.getApiByName(apiName)
            if (api != null) {
                val res = withContext(Dispatchers.IO) { api.load(url) }
                loadResponse = res
                // Default to first season
                if (res is TvSeriesLoadResponse) {
                    selectedSeason = res.episodes.mapNotNull { it.season }.minOrNull()
                }
            } else {
                errorMessage = "Provider \"$apiName\" not found."
            }
        } catch (e: Exception) {
            errorMessage = "Failed to load details: ${e.localizedMessage}"
        } finally {
            isLoading = false
        }
    }

    // Extract links and either play directly or show quality picker
    fun extractAndPlay(episode: Episode? = null) {
        val currentResponse = loadResponse ?: return
        val api = DesktopPluginLoader.getApiByName(apiName) ?: return
        extractingEpisode = episode
        coroutineScope.launch {
            isExtractingLinks = true
            val links = mutableListOf<ExtractorLink>()
            val subs = mutableListOf<SubtitleTrackInfo>()
            try {
                val episodeData = episode?.data
                    ?: (currentResponse as? MovieLoadResponse)?.dataUrl
                    ?: currentResponse.url
                withContext(Dispatchers.IO) {
                    api.loadLinks(
                        data = episodeData,
                        isCasting = false,
                        subtitleCallback = { sub ->
                            subs.add(SubtitleTrackInfo(url = sub.url, language = sub.lang))
                        },
                        callback = { link -> links.add(link) }
                    )
                }
                // Rank links: prioritize direct video files (.mkv, .mp4, .m3u8), file size metadata (GB/MB), deep paths, and quality
                fun rankLink(link: ExtractorLink): Int {
                    var score = link.quality * 100
                    val urlLower = link.url.lowercase().trimEnd('/')
                    val nameLower = link.name.lowercase()

                    if (urlLower.endsWith(".mkv") || urlLower.endsWith(".mp4") || urlLower.endsWith(".m3u8") || urlLower.endsWith(".avi")) score += 10000
                    if (nameLower.contains("gb") || nameLower.contains("mb")) score += 5000
                    val segments = urlLower.split("/").size
                    score += segments * 1000
                    return score
                }

                val sortedLinks = links.sortedByDescending { rankLink(it) }
                extractedLinks = sortedLinks
                subtitleTracks = subs

                if (sortedLinks.isNotEmpty()) {
                    // Play best direct video file link immediately, passing all sources & subs to PlayerScreen
                    val bestLink = sortedLinks.first()
                    onNavigate(DesktopScreen.Player(
                        mediaUrl = bestLink.url,
                        title = currentResponse.name,
                        episodeName = episode?.name ?: episode?.let { "S${it.season}E${it.episode}" },
                        posterUrl = currentResponse.posterUrl,
                        headers = bestLink.headers,
                        subtitles = subs,
                        sources = sortedLinks.map { com.lagradost.cloudstream.desktop.ui.navigation.StreamSourceInfo(name = it.name, url = it.url, quality = it.quality, headers = it.headers) },
                        loadResponse = currentResponse
                    ))
                } else {
                    errorMessage = "No playable links found."
                }
            } catch (e: Exception) {
                errorMessage = "Extraction failed: ${e.localizedMessage}"
            } finally {
                isExtractingLinks = false
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0D0D12))) {
        when {
            isLoading -> LoadingState()
            errorMessage != null -> ErrorState(errorMessage!!, onBack)
            loadResponse != null -> {
                val res = loadResponse!!

                // Backdrop + content layout
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // ── HERO BACKDROP ─────────────────────────────────────
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                            // Blurred backdrop
                            SubcomposeAsyncImage(
                                model = res.backgroundPosterUrl ?: searchResponse?.posterUrl ?: res.posterUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().blur(12.dp),
                                contentScale = ContentScale.Crop,
                                error = {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(
                                            Brush.linearGradient(listOf(Color(0xFF1A1A3E), Color(0xFF12122A)))
                                        )
                                    )
                                }
                            )

                            // Dark overlay
                            Box(
                                modifier = Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.55f),
                                            Color.Black.copy(alpha = 0.75f),
                                            Color(0xFF0D0D12)
                                        )
                                    )
                                )
                            )

                            // Back button
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .padding(20.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                            }

                            // Hero content
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                // Poster
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.width(140.dp).height(210.dp),
                                    elevation = CardDefaults.cardElevation(16.dp)
                                ) {
                                    SubcomposeAsyncImage(
                                        model = res.posterUrl,
                                        contentDescription = res.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        error = {
                                            Box(
                                                modifier = Modifier.fillMaxSize().background(Color(0xFF2A2A3A)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(res.name.take(2).uppercase(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    )
                                }

                                // Info column
                                Column(modifier = Modifier.weight(1f)) {
                                    // Tags row
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val tvType = searchResponse?.type?.name ?: res.type.name
                                        InfoChip(tvType)
                                        res.year?.let { InfoChip("$it") }
                                        res.score?.let { s ->
                                            InfoChip("★ ${"%.1f".format(s.toFloat(10))}", color = Color(0xFFFFD700).copy(alpha = 0.2f))
                                        }
                                    }

                                    Spacer(Modifier.height(10.dp))

                                    Text(
                                        text = res.name,
                                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = Color.White
                                    )

                                    // Genres
                                    if (!res.tags.isNullOrEmpty()) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = res.tags!!.take(4).joinToString(" • "),
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 13.sp
                                        )
                                    }

                                    Spacer(Modifier.height(10.dp))

                                    // Plot (first 3 lines)
                                    res.plot?.let { plot ->
                                        Text(
                                            text = plot,
                                            color = Color.LightGray,
                                            fontSize = 13.sp,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(Modifier.height(16.dp))

                                    // Action buttons
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        if (res is MovieLoadResponse) {
                                            Button(
                                                onClick = { extractAndPlay(null) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                                shape = RoundedCornerShape(10.dp),
                                                enabled = !isExtractingLinks
                                            ) {
                                                if (isExtractingLinks) {
                                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                                                } else {
                                                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                                                }
                                                Spacer(Modifier.width(6.dp))
                                                Text("Play Movie", color = Color.Black, fontWeight = FontWeight.Bold)
                                            }
                                        } else if (res is TvSeriesLoadResponse) {
                                            val firstEp = res.episodes.firstOrNull()
                                            Button(
                                                onClick = { firstEp?.let { extractAndPlay(it) } },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                                shape = RoundedCornerShape(10.dp),
                                                enabled = !isExtractingLinks && firstEp != null
                                            ) {
                                                if (isExtractingLinks) {
                                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                                                } else {
                                                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                                                }
                                                Spacer(Modifier.width(6.dp))
                                                Text("Play S1E1", color = Color.Black, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = { /* TODO: bookmark */ },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                        ) {
                                            Icon(Icons.Default.BookmarkBorder, null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Watchlist")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── TV SERIES EPISODES ─────────────────────────────────
                    if (res is TvSeriesLoadResponse) {
                        val seasons = res.episodes.mapNotNull { it.season }.distinct().sorted()

                        if (seasons.size > 1) {
                            item {
                                // Season tabs
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(seasons) { season ->
                                        FilterChip(
                                            selected = selectedSeason == season,
                                            onClick = { selectedSeason = season },
                                            label = { Text("Season $season", fontWeight = if (selectedSeason == season) FontWeight.Bold else FontWeight.Normal) },
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
                        } else {
                            item { Spacer(Modifier.height(16.dp)) }
                        }

                        val seasonEpisodes = if (selectedSeason != null)
                            res.episodes.filter { it.season == selectedSeason || (it.season == null && selectedSeason == 1) }
                        else res.episodes

                        item {
                            Text(
                                text = "Episodes (${seasonEpisodes.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                            )
                        }

                        itemsIndexed(seasonEpisodes) { index, ep ->
                            EpisodeRow(
                                episode = ep,
                                index = index,
                                isExtracting = isExtractingLinks && extractingEpisode == ep,
                                onClick = { extractAndPlay(ep) },
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // ── FULL PLOT ──────────────────────────────────────────
                    res.plot?.let { plot ->
                        item {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text("Synopsis", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                Spacer(Modifier.height(8.dp))
                                Text(plot, color = Color.LightGray, fontSize = 14.sp, lineHeight = 22.sp)
                            }
                        }
                    }

                    // ── CAST / ACTORS ──────────────────────────────────────
                    val actors = res.actors
                    if (!actors.isNullOrEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                                Text("Cast", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                Spacer(Modifier.height(12.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(actors.take(12)) { actorData ->
                                        ActorCard(actorData)
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                            }
                        }
                    }

                    // ── RECOMMENDATIONS ───────────────────────────────────
                    val recommendations = res.recommendations
                    if (!recommendations.isNullOrEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                                Text("More Like This", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                Spacer(Modifier.height(12.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(recommendations.take(10)) { rec ->
                                        MediaCard(
                                            item = rec,
                                            onClick = {
                                                onNavigate(DesktopScreen.Result(url = rec.url, apiName = rec.apiName, searchResponse = rec))
                                            },
                                            width = 130.dp,
                                            height = 195.dp
                                        )
                                    }
                                }
                                Spacer(Modifier.height(32.dp))
                            }
                        }
                    }

                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }

        // Extracting overlay
        if (isExtractingLinks) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2A))
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text("Finding stream links…", color = Color.White)
                    }
                }
            }
        }
    }
}

// ─── Episode Row ─────────────────────────────────────────────────────────────

@Composable
fun EpisodeRow(
    episode: Episode,
    index: Int,
    isExtracting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var hovered by remember { mutableStateOf(false) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hovered) Color(0xFF2A2A3A) else Color(0xFF1A1A22)
        ),
        elevation = CardDefaults.cardElevation(if (hovered) 6.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Episode number badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (isExtracting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                } else {
                    Text(
                        text = "${episode.episode ?: (index + 1)}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.name ?: "Episode ${episode.episode ?: (index + 1)}",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (episode.season != null) {
                    Text(
                        text = "Season ${episode.season} • Episode ${episode.episode}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                episode.description?.let { desc ->
                    Text(
                        text = desc,
                        fontSize = 11.sp,
                        color = Color.Gray.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(Icons.Default.PlayCircleOutline, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
    }
}

// ─── Actor Card ──────────────────────────────────────────────────────────────

@Composable
fun ActorCard(actorData: ActorData, modifier: Modifier = Modifier) {
    val actor = actorData.actor
    Column(
        modifier = modifier.width(80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color(0xFF2A2A3A)),
            contentAlignment = Alignment.Center
        ) {
            if (actor.image != null) {
                SubcomposeAsyncImage(
                    model = actor.image,
                    contentDescription = actor.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = {
                        Text(
                            actor.name.take(1),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                )
            } else {
                Text(
                    actor.name.take(1),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            actor.name,
            fontSize = 11.sp,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
        actorData.roleString?.let { role ->
            Text(role, fontSize = 10.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ─── Quality Picker Dialog ────────────────────────────────────────────────────

@Composable
fun QualityPickerDialog(
    links: List<ExtractorLink>,
    onSelect: (ExtractorLink) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2A)),
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Select Quality",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    "${links.size} stream sources found",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(16.dp))

                links.forEachIndexed { index, link ->
                    val qualityLabel = when {
                        link.quality >= 2160 -> "4K UHD"
                        link.quality >= 1080 -> "1080p Full HD"
                        link.quality >= 720 -> "720p HD"
                        link.quality >= 480 -> "480p SD"
                        link.quality >= 360 -> "360p Low"
                        link.quality > 0 -> "${link.quality}p"
                        else -> "Auto"
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSelect(link) },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A3A))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Quality badge
                            Surface(
                                color = when {
                                    link.quality >= 1080 -> Color(0xFF1565C0)
                                    link.quality >= 720 -> Color(0xFF2E7D32)
                                    else -> Color(0xFF6A1B9A)
                                },
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    qualityLabel,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(link.name, color = Color.White, fontWeight = FontWeight.Medium)
                                Text(
                                    link.url.take(50) + if (link.url.length > 50) "…" else "",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                            Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        }
    }
}

// ─── Info Chip ────────────────────────────────────────────────────────────────

@Composable
fun InfoChip(label: String, color: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontSize = 11.sp,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─── States ───────────────────────────────────────────────────────────────────

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Loading media details…", color = Color.Gray)
        }
    }
}

@Composable
private fun ErrorState(message: String, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text(message, color = MaterialTheme.colorScheme.error, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onBack) { Text("Go Back") }
        }
    }
}
