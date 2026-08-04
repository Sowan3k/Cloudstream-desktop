package com.lagradost.cloudstream.desktop.ui.screens.result

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.ui.draw.scale
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
import com.lagradost.cloudstream.desktop.storage.DesktopDataStore
import com.lagradost.cloudstream.desktop.storage.WatchlistItem
import com.lagradost.cloudstream.desktop.ui.components.MediaCard
import com.lagradost.cloudstream.desktop.ui.navigation.DesktopScreen
import com.lagradost.cloudstream.desktop.ui.navigation.SubtitleTrackInfo
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─── Windows 11 Color Tokens ──────────────────────────────────────────────────
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

@Composable
fun ResultScreen(
    url: String,
    apiName: String,
    searchResponse: SearchResponse? = null,
    autoPlay: Boolean = false,
    onNavigate: (DesktopScreen, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    var activeApi by remember { mutableStateOf<MainAPI?>(null) }
    var loadResponse by remember { mutableStateOf<LoadResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isExtractingLinks by remember { mutableStateOf(false) }
    var extractedLinks by remember { mutableStateOf<List<ExtractorLink>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<SubtitleTrackInfo>>(emptyList()) }
    var showQualityPicker by remember { mutableStateOf(false) }
    var selectedSeason by remember { mutableStateOf<Int?>(null) }
    var extractingEpisode by remember { mutableStateOf<Episode?>(null) }
    var hasAutoPlayed by remember { mutableStateOf(false) }
    var isInWatchlist by remember { mutableStateOf(DesktopDataStore.isInWatchlist(url)) }

    // Extract links and play directly
    fun extractAndPlay(episode: Episode? = null, targetResponse: LoadResponse? = loadResponse) {
        val currentResponse = targetResponse ?: return
        val api = activeApi ?: DesktopPluginLoader.getApiByName(apiName) ?: return
        extractingEpisode = episode
        coroutineScope.launch {
            isExtractingLinks = true
            val links = mutableListOf<ExtractorLink>()
            val subs = mutableListOf<SubtitleTrackInfo>()
            fun isAudioOnly(link: ExtractorLink?): Boolean {
                if (link == null) return false
                val url = link.url ?: return false
                val name = link.name ?: ""
                val uLower = url.lowercase()
                val nLower = name.lowercase()
                if (nLower.contains("audio track") || nLower.contains("audio-only")) return true
                if (uLower.endsWith(".m4a") || uLower.endsWith(".aac") || uLower.endsWith(".mp3") || uLower.endsWith(".mka") || uLower.endsWith(".wav") || uLower.endsWith(".flac")) return true
                if (uLower.contains(".audio.") || uLower.contains("/audio.")) return true
                return false
            }

            try {
                val episodeData = episode?.data
                    ?: (currentResponse as? MovieLoadResponse)?.dataUrl
                    ?: currentResponse.url
                withContext(Dispatchers.IO) {
                    kotlinx.coroutines.withTimeoutOrNull(90_000L) {
                        try {
                            api.loadLinks(
                                data = episodeData,
                                isCasting = false,
                                subtitleCallback = { sub ->
                                    if (sub != null && !sub.url.isNullOrBlank()) {
                                        subs.add(SubtitleTrackInfo(url = sub.url, language = sub.lang ?: "Subtitle"))
                                    }
                                },
                                callback = { link ->
                                    if (link != null && !link.url.isNullOrBlank()) {
                                        links.add(link)
                                    }
                                }
                            )
                        } catch (_: Exception) {}
                    }
                }

                fun rankLink(link: ExtractorLink?): Int {
                    if (link == null) return -999999
                    val url = link.url ?: return -999999
                    var score = link.quality * 100
                    val urlLower = url.lowercase().trimEnd('/')
                    val nameLower = (link.name ?: "").lowercase()

                    if (isAudioOnly(link)) score -= 100000

                    if (urlLower.endsWith(".mkv") || urlLower.endsWith(".mp4") || urlLower.endsWith(".m3u8") || urlLower.endsWith(".avi")) score += 10000
                    if (nameLower.contains("gb") || nameLower.contains("mb")) score += 5000
                    val segments = urlLower.split("/").size
                    score += segments * 1000
                    return score
                }

                val nonNullLinks = links.filterNotNull().filter { !it.url.isNullOrBlank() }
                val videoOnlyLinks = nonNullLinks.filterNot { isAudioOnly(it) }
                val validLinks = videoOnlyLinks.ifEmpty { nonNullLinks }
                val sortedLinks = validLinks.sortedByDescending { rankLink(it) }
                extractedLinks = sortedLinks
                subtitleTracks = subs

                if (sortedLinks.isNotEmpty()) {
                    val bestLink = sortedLinks.first()
                    onNavigate(
                        DesktopScreen.Player(
                            mediaUrl = bestLink.url,
                            title = currentResponse.name,
                            episodeName = episode?.let { ep ->
                                val code = "S${ep.season ?: 1}E${ep.episode ?: 1}"
                                val name = ep.name
                                if (!name.isNullOrBlank() && !name.contains(code, ignoreCase = true)) {
                                    "$code — $name"
                                } else if (!name.isNullOrBlank()) {
                                    name
                                } else {
                                    code
                                }
                            },
                            posterUrl = currentResponse.posterUrl,
                            headers = bestLink.headers,
                            subtitles = subs,
                            sources = sortedLinks.map { com.lagradost.cloudstream.desktop.ui.navigation.StreamSourceInfo(name = it.name ?: "Server", url = it.url, quality = it.quality, headers = it.headers) },
                            loadResponse = currentResponse,
                            currentEpisode = episode,
                            apiName = api.name
                        ),
                        autoPlay
                    )
                } else {
                    errorMessage = "No playable links found for this title."
                }
            } catch (e: Exception) {
                val nonNullLinks = links.filterNotNull().filter { !it.url.isNullOrBlank() }
                if (nonNullLinks.isNotEmpty()) {
                    val videoOnlyLinks = nonNullLinks.filterNot { isAudioOnly(it) }
                    val sortedLinks = videoOnlyLinks.ifEmpty { nonNullLinks }
                    if (sortedLinks.isNotEmpty()) {
                        val bestLink = sortedLinks.first()
                        onNavigate(
                            DesktopScreen.Player(
                                mediaUrl = bestLink.url,
                                title = currentResponse.name,
                                episodeName = episode?.let { ep -> "S${ep.season ?: 1}E${ep.episode ?: 1}" },
                                posterUrl = currentResponse.posterUrl,
                                headers = bestLink.headers,
                                subtitles = subs,
                                sources = sortedLinks.map { com.lagradost.cloudstream.desktop.ui.navigation.StreamSourceInfo(name = it.name ?: "Server", url = it.url, quality = it.quality, headers = it.headers) },
                                loadResponse = currentResponse,
                                currentEpisode = episode,
                                apiName = api.name
                            ),
                            autoPlay
                        )
                    } else {
                        errorMessage = "Extraction failed: ${e.localizedMessage}"
                    }
                } else {
                    errorMessage = "Extraction failed: ${e.localizedMessage}"
                }
            } finally {
                isExtractingLinks = false
            }
        }
    }

    // Load media details
    LaunchedEffect(url, apiName) {
        isLoading = true
        errorMessage = null
        try {
            val resolvedApi = DesktopPluginLoader.getApiByName(apiName)
                ?: (if (searchResponse != null) DesktopPluginLoader.getApiByName(searchResponse.apiName) else null)
                ?: DesktopPluginLoader.registeredApis.value.firstOrNull { loaded ->
                    try {
                        val loadedHost = java.net.URI(loaded.mainUrl).host
                        val targetHost = java.net.URI(url).host
                        loadedHost != null && targetHost != null && loadedHost.equals(targetHost, ignoreCase = true)
                    } catch (_: Exception) {
                        url.startsWith(loaded.mainUrl)
                    }
                }
                ?: DesktopPluginLoader.registeredApis.value.firstOrNull()

            activeApi = resolvedApi
            if (resolvedApi != null) {
                val res = withContext(Dispatchers.IO) { resolvedApi.load(url) }
                loadResponse = res
                if (res is TvSeriesLoadResponse) {
                    selectedSeason = res.episodes.mapNotNull { it.season }.minOrNull()
                }
                if (autoPlay && !hasAutoPlayed) {
                    hasAutoPlayed = true
                    val epToPlay = if (res is TvSeriesLoadResponse && res.episodes.isNotEmpty()) {
                        val historyMatch = DesktopDataStore.currentSettings.watchHistory.firstOrNull { it.url == url }
                        if (historyMatch != null) {
                            val sNum = historyMatch.seasonIndex ?: Regex("""S(\d+)E(\d+)""", RegexOption.IGNORE_CASE)
                                .find(historyMatch.title)?.groupValues?.get(1)?.toIntOrNull()
                            val eNum = historyMatch.episodeIndex ?: Regex("""S(\d+)E(\d+)""", RegexOption.IGNORE_CASE)
                                .find(historyMatch.title)?.groupValues?.get(2)?.toIntOrNull()

                            if (sNum != null && eNum != null) {
                                res.episodes.firstOrNull { ep -> ep.season == sNum && ep.episode == eNum }
                            } else if (eNum != null) {
                                res.episodes.firstOrNull { ep -> ep.episode == eNum }
                            } else null
                            ?: res.episodes.first()
                        } else {
                            res.episodes.first()
                        }
                    } else null
                    extractAndPlay(episode = epToPlay, targetResponse = res)
                }
            } else {
                errorMessage = "No provider plugin available to load this title."
            }
        } catch (e: Exception) {
            errorMessage = "Failed to load details: ${e.localizedMessage}"
        } finally {
            isLoading = false
        }
    }

    Box(modifier = modifier.fillMaxSize().background(SurfaceBase)) {
        when {
            autoPlay && (isLoading || isExtractingLinks) -> Win11ResumingState()
            isLoading -> Win11LoadingState()
            errorMessage != null -> Win11ErrorState(errorMessage!!, onBack)
            loadResponse != null -> {
                val res = loadResponse!!

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // ── HERO BACKDROP ─────────────────────────────────────────
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(380.dp)) {
                            // Blurred backdrop image
                            SubcomposeAsyncImage(
                                model = res.backgroundPosterUrl ?: searchResponse?.posterUrl ?: res.posterUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().blur(16.dp),
                                contentScale = ContentScale.Crop,
                                error = {
                                    Box(modifier = Modifier.fillMaxSize().background(SurfaceLayer))
                                }
                            )

                            // Win11 Dark Gradient overlay
                            Box(
                                modifier = Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.5f),
                                            Color.Black.copy(alpha = 0.75f),
                                            SurfaceBase
                                        )
                                    )
                                )
                            )

                            // Top Back button
                            Box(modifier = Modifier.padding(20.dp)) {
                                Win11IconButton(
                                    icon = Icons.Default.ArrowBack,
                                    tooltip = "Back",
                                    onClick = onBack
                                )
                            }

                            // Hero Content Info
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .padding(horizontal = 28.dp, vertical = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                // Poster Surface Card
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SurfaceCard,
                                    border = BorderStroke(1.dp, BorderDefault),
                                    modifier = Modifier.width(140.dp).height(210.dp)
                                ) {
                                    SubcomposeAsyncImage(
                                        model = res.posterUrl,
                                        contentDescription = res.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        error = {
                                            Box(
                                                modifier = Modifier.fillMaxSize().background(SurfaceLayer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    res.name.take(2).uppercase(),
                                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                                    color = TextTertiary
                                                )
                                            }
                                        }
                                    )
                                }

                                // Details info column
                                Column(modifier = Modifier.weight(1f)) {
                                    // Info Chips (Type, Year, Rating)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val tvType = searchResponse?.type?.name ?: res.type.name
                                        Win11InfoChip(tvType)
                                        res.year?.let { Win11InfoChip("$it") }
                                        res.score?.let { s ->
                                            Win11InfoChip("★ ${"%.1f".format(s.toFloat(10))}", isAccent = true)
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    Text(
                                        text = res.name,
                                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // Genres
                                    if (!res.tags.isNullOrEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = res.tags!!.take(4).joinToString("  •  "),
                                            color = AccentBlue,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    // Plot (shortened)
                                    res.plot?.let { plot ->
                                        Text(
                                            text = plot,
                                            color = TextSecondary,
                                            fontSize = 13.sp,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 18.sp
                                        )
                                    }

                                    Spacer(Modifier.height(16.dp))

                                    // Action buttons
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        if (res is MovieLoadResponse) {
                                            Surface(
                                                onClick = { extractAndPlay(null) },
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color.White,
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 16.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    if (isExtractingLinks) {
                                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                                                    } else {
                                                        Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                                    }
                                                    Text("Play Movie", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                }
                                            }
                                        } else if (res is TvSeriesLoadResponse) {
                                            val historyMatch = DesktopDataStore.currentSettings.watchHistory.firstOrNull { it.url == url }
                                            val epToPlay = if (historyMatch != null) {
                                                val targetSeason = historyMatch.seasonIndex
                                                val targetEpNum = historyMatch.episodeIndex
                                                res.episodes.firstOrNull { ep ->
                                                    (targetSeason == null || ep.season == targetSeason) &&
                                                    (targetEpNum == null || ep.episode == targetEpNum)
                                                } ?: res.episodes.firstOrNull()
                                            } else res.episodes.firstOrNull()

                                            Surface(
                                                onClick = { epToPlay?.let { extractAndPlay(it) } },
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color.White,
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 16.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    if (isExtractingLinks) {
                                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                                                    } else {
                                                        Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                                    }
                                                    val epLabel = if (epToPlay != null) {
                                                        val s = epToPlay.season ?: 1
                                                        val e = epToPlay.episode ?: 1
                                                        if (historyMatch != null) "Resume S${s}E${e}" else "Play S${s}E${e}"
                                                    } else "Play"
                                                    Text(epLabel, color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                }
                                            }
                                        }

                                        // Watchlist Button
                                        Surface(
                                            onClick = {
                                                val currentRes = loadResponse
                                                if (currentRes != null) {
                                                    if (isInWatchlist) {
                                                        DesktopDataStore.removeFromWatchlist(url)
                                                    } else {
                                                        DesktopDataStore.addToWatchlist(
                                                            WatchlistItem(
                                                                title = currentRes.name,
                                                                url = url,
                                                                posterUrl = currentRes.posterUrl,
                                                                apiName = apiName,
                                                                tvType = currentRes.type?.name ?: ""
                                                            )
                                                        )
                                                    }
                                                    isInWatchlist = !isInWatchlist
                                                }
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isInWatchlist) AccentBlue.copy(0.15f) else Color.White.copy(0.12f),
                                            border = BorderStroke(1.dp, if (isInWatchlist) AccentBlue.copy(0.5f) else BorderSubtle),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    if (isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                    null,
                                                    tint = if (isInWatchlist) AccentBlue else TextPrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    if (isInWatchlist) "Bookmarked" else "Watchlist",
                                                    color = if (isInWatchlist) AccentBlue else TextPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isInWatchlist) FontWeight.SemiBold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── TV SERIES EPISODES ────────────────────────────────────
                    if (res is TvSeriesLoadResponse) {
                        val seasons = res.episodes.mapNotNull { it.season }.distinct().sorted()

                        if (seasons.size > 1) {
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(seasons) { season ->
                                        val isSelected = selectedSeason == season
                                        val textColor by animateColorAsState(if (isSelected) TextPrimary else TextTertiary, tween(150))

                                        Surface(
                                            onClick = { selectedSeason = season },
                                            shape = RoundedCornerShape(5.dp),
                                            color = if (isSelected) AccentBlue.copy(0.12f) else Color.Transparent,
                                            border = BorderStroke(1.dp, if (isSelected) AccentBlue.copy(0.5f) else BorderSubtle)
                                        ) {
                                            Text(
                                                "Season $season",
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                                color = textColor,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            item { Spacer(Modifier.height(12.dp)) }
                        }

                        val seasonEpisodes = if (selectedSeason != null)
                            res.episodes.filter { it.season == selectedSeason || (it.season == null && selectedSeason == 1) }
                        else res.episodes

                        item {
                            Text(
                                text = "Episodes (${seasonEpisodes.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp)
                            )
                        }

                        itemsIndexed(seasonEpisodes) { index, ep ->
                            Win11EpisodeRow(
                                episode = ep,
                                index = index,
                                isExtracting = isExtractingLinks && extractingEpisode == ep,
                                onClick = { extractAndPlay(ep) },
                                modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // ── SYNOPSIS ─────────────────────────────────────────────
                    res.plot?.let { plot ->
                        item {
                            Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp)) {
                                Text("Synopsis", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Spacer(Modifier.height(8.dp))
                                Text(plot, color = TextSecondary, fontSize = 13.sp, lineHeight = 20.sp)
                            }
                        }
                    }

                    // ── CAST / ACTORS ─────────────────────────────────────────
                    val actors = res.actors
                    if (!actors.isNullOrEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)) {
                                Text("Cast", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Spacer(Modifier.height(12.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    items(actors.take(12)) { actorData ->
                                        Win11ActorCard(actorData)
                                    }
                                }
                            }
                        }
                    }

                    // ── RECOMMENDATIONS ──────────────────────────────────────
                    val recommendations = res.recommendations
                    if (!recommendations.isNullOrEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp)) {
                                Text("More Like This", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Spacer(Modifier.height(12.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(recommendations.take(10)) { rec ->
                                        MediaCard(
                                            item = rec,
                                            onClick = {
                                                onNavigate(DesktopScreen.Result(url = rec.url, apiName = rec.apiName, searchResponse = rec), false)
                                            },
                                            width = 130.dp,
                                            height = 190.dp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }

        // Extracting Overlay
        if (isExtractingLinks) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceCard,
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AccentBlue, strokeWidth = 2.dp)
                        Spacer(Modifier.height(14.dp))
                        Text("Finding stream links...", color = TextPrimary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ─── Win11 Icon Button ────────────────────────────────────────────────────────
@Composable
private fun Win11IconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tooltip: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (hovered) SurfaceHover else Color.Black.copy(0.45f),
        border = BorderStroke(1.dp, if (hovered) BorderDefault else BorderSubtle),
        modifier = Modifier.size(32.dp).hoverable(interactionSource)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, tooltip, tint = TextPrimary, modifier = Modifier.size(16.dp))
        }
    }
}

// ─── Win11 Info Chip ──────────────────────────────────────────────────────────
@Composable
private fun Win11InfoChip(label: String, isAccent: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (isAccent) Color(0xFFFFD700).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, if (isAccent) Color(0xFFFFD700).copy(alpha = 0.3f) else BorderSubtle)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            fontSize = 11.sp,
            color = if (isAccent) Color(0xFFFFD700) else TextSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}

// ─── Episode Row Component ────────────────────────────────────────────────────
@Composable
private fun Win11EpisodeRow(
    episode: Episode,
    index: Int,
    isExtracting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (hovered) SurfaceHover else SurfaceCard,
        border = BorderStroke(1.dp, if (hovered) BorderDefault else BorderSubtle),
        modifier = modifier.fillMaxWidth().hoverable(interactionSource)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Episode Number Badge
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = AccentBlue.copy(alpha = 0.12f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isExtracting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AccentBlue, strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = "${episode.episode ?: (index + 1)}",
                            color = AccentBlue,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.name ?: "Episode ${episode.episode ?: (index + 1)}",
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (episode.season != null) {
                    Text(
                        text = "Season ${episode.season} • Episode ${episode.episode}",
                        fontSize = 11.sp,
                        color = TextTertiary
                    )
                }
                episode.description?.let { desc ->
                    Text(
                        text = desc,
                        fontSize = 11.sp,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                Icons.Default.PlayArrow,
                null,
                tint = if (hovered) AccentBlue else TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─── Actor Card Component ─────────────────────────────────────────────────────
@Composable
private fun Win11ActorCard(actorData: ActorData, modifier: Modifier = Modifier) {
    val actor = actorData.actor
    Column(
        modifier = modifier.width(76.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = SurfaceLayer,
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier.size(64.dp)
        ) {
            if (actor.image != null) {
                SubcomposeAsyncImage(
                    model = actor.image,
                    contentDescription = actor.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = {
                        Box(contentAlignment = Alignment.Center) {
                            Text(actor.name.take(1), color = TextTertiary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(actor.name.take(1), color = TextTertiary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            actor.name,
            fontSize = 11.sp,
            color = TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
        actorData.roleString?.let { role ->
            Text(role, fontSize = 10.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ─── States ───────────────────────────────────────────────────────────────────
@Composable
private fun Win11LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = AccentBlue, strokeWidth = 2.dp)
            Text("Loading media details...", color = TextTertiary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Win11ResumingState() {
    Box(modifier = Modifier.fillMaxSize().background(SurfaceBase), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp), color = AccentBlue, strokeWidth = 2.dp)
            Text("Resuming playback...", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("Fetching stream links", color = TextTertiary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Win11ErrorState(message: String, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFCF6679), modifier = Modifier.size(40.dp))
            Text(message, color = TextSecondary, fontSize = 13.sp)
            Surface(
                onClick = onBack,
                shape = RoundedCornerShape(6.dp),
                color = SurfaceLayer,
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Text("Go Back", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = TextPrimary, fontSize = 12.sp)
            }
        }
    }
}
