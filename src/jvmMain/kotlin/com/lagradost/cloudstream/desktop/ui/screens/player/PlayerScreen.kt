package com.lagradost.cloudstream.desktop.ui.screens.player

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lagradost.cloudstream.desktop.plugins.DesktopPluginLoader
import com.lagradost.cloudstream.desktop.storage.DesktopDataStore
import com.lagradost.cloudstream.desktop.storage.WatchProgress
import com.lagradost.cloudstream.desktop.ui.navigation.StreamSourceInfo
import com.lagradost.cloudstream.desktop.ui.navigation.SubtitleTrackInfo
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.TrackDescription
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import com.sun.jna.Native
import com.sun.jna.WString
import java.awt.Desktop
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.File
import java.net.URI
import java.nio.ByteBuffer

// ─── VLC Discovery ───────────────────────────────────────────────────────────

private val VLC_WINDOWS_PATHS = listOf(
    "C:\\Program Files\\VideoLAN\\VLC",
    "C:\\Program Files (x86)\\VideoLAN\\VLC"
)
private fun findVlcPath(): String? = VLC_WINDOWS_PATHS.firstOrNull { File(it, "libvlc.dll").exists() }

private interface WinKernel32 : com.sun.jna.Library {
    fun SetEnvironmentVariableW(name: WString, value: WString): Boolean
}

private fun setVlcEnvironment(name: String, value: String) {
    // JNA uses system properties, while LibVLC reads its paths from the process environment.
    System.setProperty(name, value)
    try {
        Native.load("kernel32", WinKernel32::class.java)
            .SetEnvironmentVariableW(WString(name), WString(value))
    } catch (_: Throwable) {
        // NativeDiscovery remains available as a fallback outside Windows.
    }
}

private fun tryConfigureVlc(): String? {
    val path = findVlcPath()
    if (path != null) {
        setVlcEnvironment("jna.library.path", path)
        setVlcEnvironment("java.library.path", path)
        setVlcEnvironment("VLC_LIB_PATH", path)
        setVlcEnvironment("VLC_DATA_PATH", path)
        setVlcEnvironment("VLC_PLUGIN_PATH", File(path, "plugins").absolutePath)
        return path
    }
    return try { if (NativeDiscovery().discover()) "" else null } catch (_: Exception) { null }
}

private fun toVlcMrl(url: String): String = try {
    if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
        val decoded = try { java.net.URLDecoder.decode(url, "UTF-8") } catch (_: Exception) { url }
        val parsed = java.net.URI(decoded).toURL()
        val uri = URI(parsed.protocol, parsed.userInfo, parsed.host, parsed.port, parsed.path, parsed.query, parsed.ref)
        uri.toASCIIString()
    } else {
        url.replace(" ", "%20")
    }
} catch (_: Exception) {
    url.replace(" ", "%20")
}

private fun logVlc(message: String) {
    try {
        File(System.getProperty("user.home"), "cloudstream_vlc_debug.log")
            .appendText("${java.time.LocalDateTime.now()}: $message\n")
    } catch (_: Exception) {}
}

enum class VideoAspectMode(val label: String, val scale: ContentScale) {
    CROP("Zoom / Fill Screen", ContentScale.Crop),
    STRETCH("Stretch to Fill", ContentScale.FillBounds),
    FIT("Original / Fit", ContentScale.Fit)
}

// ─── PlayerScreen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    mediaUrl: String,
    title: String,
    episodeName: String? = null,
    posterUrl: String? = null,
    headers: Map<String, String> = emptyMap(),
    subtitles: List<SubtitleTrackInfo> = emptyList(),
    sources: List<StreamSourceInfo> = emptyList(),
    loadResponse: LoadResponse? = null,
    currentEpisode: Episode? = null,
    apiName: String = "",
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    // ── TV Series Episodes State ──────────────────────────────────────────────
    val allEpisodes: List<Episode> = remember(loadResponse) {
        when (loadResponse) {
            is TvSeriesLoadResponse -> loadResponse.episodes
            is AnimeLoadResponse -> loadResponse.episodes.values.flatten()
            else -> emptyList()
        }
    }
    var currentEp by remember { mutableStateOf(currentEpisode) }
    var activeEpisodeName by remember {
        mutableStateOf(episodeName ?: currentEpisode?.let { "S${it.season}E${it.episode}" })
    }
    var activeSourcesList by remember { mutableStateOf(sources) }
    var activeSubtitlesList by remember { mutableStateOf(subtitles) }
    var isExtractingNextEp by remember { mutableStateOf(false) }
    var showEpisodesPicker by remember { mutableStateOf(false) }

    val currentEpIndex = remember(currentEp, activeEpisodeName, allEpisodes) {
        if (allEpisodes.isEmpty()) -1
        else {
            val byData = allEpisodes.indexOfFirst { ep -> currentEp != null && (ep == currentEp || ep.data == currentEp?.data) }
            if (byData != -1) byData
            else if (currentEp?.season != null && currentEp?.episode != null) {
                val bySeasonEp = allEpisodes.indexOfFirst { ep -> ep.season == currentEp?.season && ep.episode == currentEp?.episode }
                if (bySeasonEp != -1) bySeasonEp
                else allEpisodes.indexOfFirst { ep -> ep.episode == currentEp?.episode }
            } else if (!activeEpisodeName.isNullOrBlank()) {
                val match = Regex("""S(\d+)E(\d+)""", RegexOption.IGNORE_CASE).find(activeEpisodeName!!)
                val sNum = match?.groupValues?.get(1)?.toIntOrNull()
                val eNum = match?.groupValues?.get(2)?.toIntOrNull()
                if (sNum != null && eNum != null) {
                    val byParsed = allEpisodes.indexOfFirst { ep -> ep.season == sNum && ep.episode == eNum }
                    if (byParsed != -1) byParsed
                    else allEpisodes.indexOfFirst { ep -> ep.episode == eNum }
                } else {
                    allEpisodes.indexOfFirst { ep -> ep.name == activeEpisodeName }
                }
            } else -1
        }
    }
    val hasPrevEpisode = allEpisodes.isNotEmpty() && currentEpIndex > 0
    val hasNextEpisode = allEpisodes.isNotEmpty() && currentEpIndex != -1 && currentEpIndex < (allEpisodes.size - 1)

    // ── Active Stream Source ──────────────────────────────────────────────────
    var activeSource by remember {
        mutableStateOf(
            activeSourcesList.firstOrNull { it.url == mediaUrl }
                ?: StreamSourceInfo(name = "Primary Stream", url = mediaUrl, quality = 0, headers = headers)
        )
    }

    var videoAspectMode by remember { mutableStateOf(VideoAspectMode.CROP) }
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var volume by remember { mutableStateOf(100) }          // 0..200
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    val initialPosition = remember {
        val targetUrl = loadResponse?.url ?: mediaUrl
        val cleanTitle = title.trim().lowercase()
        val epObj = currentEpisode ?: (if (currentEpIndex != -1) allEpisodes.getOrNull(currentEpIndex) else null)
        val targetSeason = epObj?.season
        val targetEpisode = epObj?.episode

        val historyMatch = DesktopDataStore.currentSettings.watchHistory.firstOrNull { h ->
            val hUrl = h.url.trim().lowercase()
            val hTitle = h.title.trim().lowercase()
            val tUrl = targetUrl.trim().lowercase()
            val mUrl = mediaUrl.trim().lowercase()
            val sUrl = activeSource.url.trim().lowercase()

            val urlOrTitleMatch = (hUrl == tUrl || hUrl == mUrl || hUrl == sUrl ||
                    hTitle == cleanTitle ||
                    (cleanTitle.length > 5 && hTitle.startsWith(cleanTitle)))

            if (targetSeason != null && targetEpisode != null) {
                urlOrTitleMatch && h.seasonIndex == targetSeason && h.episodeIndex == targetEpisode
            } else if (!episodeName.isNullOrBlank()) {
                urlOrTitleMatch && (h.title.contains(episodeName, ignoreCase = true) || hTitle.contains(episodeName.lowercase()))
            } else {
                urlOrTitleMatch
            }
        }
        if (historyMatch != null && historyMatch.positionMillis > 3000L) {
            if (historyMatch.durationMillis <= 0 || (historyMatch.durationMillis - historyMatch.positionMillis) > 10000L) {
                historyMatch.positionMillis
            } else 0L
        } else 0L
    }
    var currentPosition by remember { mutableStateOf(initialPosition) }
    var totalDuration by remember { mutableStateOf(1L) }
    var videoFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    var isVideoReady by remember { mutableStateOf(false) }
    var vlcError by remember { mutableStateOf<String?>(null) }
    var isLocked by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(false) }

    // ── Dialog states ─────────────────────────────────────────────────────────
    var showSourcesPicker by remember { mutableStateOf(false) }
    var showAudioTrackPicker by remember { mutableStateOf(false) }
    var showSubtitlePicker by remember { mutableStateOf(false) }
    var showSpeedPicker by remember { mutableStateOf(false) }
    var showVolumePicker by remember { mutableStateOf(false) }
    var selectedSubtitle by remember { mutableStateOf<SubtitleTrackInfo?>(activeSubtitlesList.firstOrNull()) }
    var seekIndicator by remember { mutableStateOf<String?>(null) }

    // ── TV Series Episode Switching Function ─────────────────────────────────
    fun playEpisode(ep: Episode) {
        if (isExtractingNextEp) return
        val targetApiName = apiName.ifBlank { loadResponse?.apiName ?: "" }
        val api = DesktopPluginLoader.getApiByName(targetApiName) ?: return

        coroutineScope.launch {
            isExtractingNextEp = true
            vlcError = null
            isBuffering = true
            currentPosition = 0L
            try {
                val links = mutableListOf<ExtractorLink>()
                val subs = mutableListOf<SubtitleTrackInfo>()
                withContext(Dispatchers.IO) {
                    api.loadLinks(
                        data = ep.data,
                        isCasting = false,
                        subtitleCallback = { sub: com.lagradost.cloudstream3.SubtitleFile ->
                            subs.add(SubtitleTrackInfo(url = sub.url, language = sub.lang))
                        },
                        callback = { link: ExtractorLink -> links.add(link) }
                    )
                }
                fun rankLink(link: ExtractorLink): Int {
                    var score = link.quality * 100
                    val urlLower = link.url.lowercase().trimEnd('/')
                    val nameLower = link.name.lowercase()
                    if (urlLower.endsWith(".mkv") || urlLower.endsWith(".mp4") || urlLower.endsWith(".m3u8")) score += 10000
                    if (nameLower.contains("gb") || nameLower.contains("mb")) score += 5000
                    return score
                }
                val sortedLinks = links.sortedByDescending { rankLink(it) }
                if (sortedLinks.isNotEmpty()) {
                    val best = sortedLinks.first()
                    currentEp = ep
                    val epCode = "S${ep.season ?: 1}E${ep.episode ?: 1}"
                    val epNameStr = ep.name
                    activeEpisodeName = if (!epNameStr.isNullOrBlank() && !epNameStr.contains(epCode, ignoreCase = true)) {
                        "$epCode — $epNameStr"
                    } else if (!epNameStr.isNullOrBlank()) {
                        epNameStr
                    } else {
                        epCode
                    }
                    val newSources = sortedLinks.map { StreamSourceInfo(name = it.name, url = it.url, quality = it.quality, headers = it.headers) }
                    activeSourcesList = newSources
                    activeSubtitlesList = subs
                    selectedSubtitle = subs.firstOrNull()
                    activeSource = StreamSourceInfo(name = best.name, url = best.url, quality = best.quality, headers = best.headers)
                } else {
                    vlcError = "No playable links found for ${ep.name ?: "Episode ${ep.episode}"}"
                }
            } catch (e: Exception) {
                vlcError = "Episode load failed: ${e.localizedMessage}"
            } finally {
                isExtractingNextEp = false
            }
        }
    }

    // ── Native Decoded Tracks ──────────────────────────────────────────────────
    var audioTracks by remember { mutableStateOf<List<TrackDescription>>(emptyList()) }
    var nativeSubTracks by remember { mutableStateOf<List<TrackDescription>>(emptyList()) }
    var selectedAudioTrackId by remember { mutableStateOf<Int?>(null) }
    var selectedSubTrackId by remember { mutableStateOf<Int?>(null) }

    // ── Zero-GC Pooled Frame Buffer & Lock-free Frame Ticker ─────────────────
    class RawFrameData(val pixels: IntArray, val width: Int, val height: Int)
    val arrayPool = remember { java.util.concurrent.ConcurrentLinkedQueue<IntArray>() }

    fun obtainPixelArray(size: Int): IntArray {
        val existing = arrayPool.poll()
        return if (existing != null && existing.size >= size) existing else IntArray(size)
    }

    fun recyclePixelArray(arr: IntArray) {
        if (arrayPool.size < 8) arrayPool.offer(arr)
    }

    val pendingRawFrame = remember { java.util.concurrent.atomic.AtomicReference<RawFrameData?>(null) }
    val frameBuffer = remember {
        object {
            var bufferedImage: BufferedImage? = null
            var w = 0; var h = 0
            fun getOrCreate(width: Int, height: Int): BufferedImage {
                if (bufferedImage == null || w != width || h != height) {
                    w = width; h = height
                    bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
                }
                return bufferedImage!!
            }
        }
    }

    // 60fps Frame Ticker — zero-alloc transfer of raw pixels to Compose ImageBitmap
    LaunchedEffect(Unit) {
        while (isActive) {
            val raw = pendingRawFrame.getAndSet(null)
            if (raw != null) {
                try {
                    val image = frameBuffer.getOrCreate(raw.width, raw.height)
                    val target = (image.raster.dataBuffer as DataBufferInt).data
                    System.arraycopy(raw.pixels, 0, target, 0, raw.pixels.size.coerceAtMost(target.size))
                    videoFrame = image.toComposeImageBitmap()
                    isVideoReady = true
                    if (isBuffering) isBuffering = false
                } catch (_: Exception) {
                } finally {
                    recyclePixelArray(raw.pixels)
                }
            }
            delay(16)
        }
    }

    // ── VLC Setup (Created ONCE for lifetime of PlayerScreen) ───────────────────
    val factoryAndPlayer = remember {
        val vlcPath = tryConfigureVlc() ?: run {
            vlcError = "VLC not found. Install from videolan.org"
            return@remember null
        }
        try {
            val args = buildList {
                add("--quiet")
                add("--no-video-title-show")
                add("--avcodec-threads=0")       // Auto multi-threaded CPU decoding
                add("--network-caching=2500")    // Optimized 2.5s network buffer for fast stream start
                add("--file-caching=2500")       // 2.5s file buffer
                add("--live-caching=2500")
                add("--http-reconnect")
                add("--clock-jitter=0")
                add("--clock-synchro=0")
                add("--no-stats")
            }
            val factory = MediaPlayerFactory(*args.toTypedArray())
            val player = factory.mediaPlayers().newEmbeddedMediaPlayer()

            val bufferFmt = object : BufferFormatCallback {
                override fun getBufferFormat(width: Int, height: Int): BufferFormat {
                    // Cap max pixel buffer to 1920x1080 for 4K streams (prevents memory copy bottleneck)
                    val targetW = if (width > 1920) 1920 else width
                    val targetH = if (width > 1920) (height * 1920) / width else height
                    return RV32BufferFormat(targetW, targetH)
                }
                override fun allocatedBuffers(buffers: Array<ByteBuffer>) {}
            }

            val render = object : RenderCallback {
                override fun display(
                    mp: MediaPlayer,
                    nativeBuffers: Array<out ByteBuffer>,
                    fmt: uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
                ) {
                    try {
                        val fw = fmt.width; val fh = fmt.height
                        if (fw <= 0 || fh <= 0) return
                        val buffer = nativeBuffers.firstOrNull() ?: return
                        buffer.rewind()
                        val ints = buffer.asIntBuffer()
                        val count = (fw * fh).coerceAtMost(ints.remaining())
                        if (count > 0) {
                            val pixels = obtainPixelArray(count)
                            ints.get(pixels, 0, count)
                            val oldFrame = pendingRawFrame.getAndSet(RawFrameData(pixels, fw, fh))
                            if (oldFrame != null) {
                                recyclePixelArray(oldFrame.pixels)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            val surface = factory.videoSurfaces().newVideoSurface(bufferFmt, render, true)
            player.videoSurface().set(surface)

            player.events().addMediaPlayerEventListener(object : uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter() {
                override fun error(mp: MediaPlayer) {
                    logVlc("LibVLC error while playing: ${toVlcMrl(activeSource.url)}")
                    javax.swing.SwingUtilities.invokeLater { vlcError = "VLC playback error — try External VLC" }
                }
                override fun buffering(mp: MediaPlayer, newCache: Float) {
                    javax.swing.SwingUtilities.invokeLater {
                        if (newCache >= 100f) {
                            isBuffering = false
                        } else if (!mp.status().isPlaying) {
                            isBuffering = true
                        }
                    }
                }
                override fun playing(mp: MediaPlayer) {
                    javax.swing.SwingUtilities.invokeLater {
                        isBuffering = false
                        isPlaying = true
                        isVideoReady = true
                    }
                }
                override fun paused(mp: MediaPlayer) {
                    javax.swing.SwingUtilities.invokeLater {
                        isPlaying = false
                    }
                }
                override fun stopped(mp: MediaPlayer) {
                    javax.swing.SwingUtilities.invokeLater {
                        isVideoReady = false
                        isPlaying = false
                    }
                }
            })

            Pair(factory, player)
        } catch (e: Exception) {
            vlcError = e.message ?: "Failed to initialize VLC"
            null
        }
    }

    val player = factoryAndPlayer?.second

    // ── Smooth Server / Source Switching with Seamless Position Resume ──────────
    LaunchedEffect(activeSource.url) {
        if (player != null) {
            val savedPos = currentPosition // Preserves timestamp across source server switches
            vlcError = null
            videoFrame = null
            isVideoReady = false
            pendingRawFrame.set(null)
            isBuffering = true
            isPlaying = true

            val safeUrl = toVlcMrl(activeSource.url)
            val opts = buildList {
                activeSource.headers.forEach { (k, v) -> add(":http-header-$k=$v") }
                if (activeSource.headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                    add(":http-user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                }
                add(":network-caching=3000")
                add(":file-caching=3000")
                add(":live-caching=3000")
                add(":http-reconnect=1")
                add(":clock-jitter=0")
                add(":clock-synchro=0")
            }.toTypedArray()

            try {
                logVlc("Starting LibVLC MRL: $safeUrl (Resuming pos: ${savedPos}ms)")
                player.media().play(safeUrl, *opts)
                if (savedPos > 1000L) {
                    launch {
                        for (attempt in 1..10) {
                            delay(350)
                            try {
                                if (player.status().isPlaying || player.status().length() > 0) {
                                    player.controls().setTime(savedPos)
                                    currentPosition = savedPos
                                    break
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                vlcError = "Failed to switch source: ${e.message}"
                isBuffering = false
            }
        }
    }

    // ── Polling & Track Sync ───────────────────────────────────────────────────
    LaunchedEffect(player, currentEpIndex, hasNextEpisode, isExtractingNextEp) {
        while (isActive) {
            delay(500)
            try {
                val dur = player?.status()?.length() ?: 0L
                val pos = player?.status()?.time() ?: 0L
                if (dur > 0) { totalDuration = dur; currentPosition = pos }

                // Periodic Watch Progress save (every ~5 seconds)
                if (pos > 3000L && dur > 10_000L && (pos % 5000L < 600L)) {
                    val resolvedPoster = posterUrl ?: loadResponse?.posterUrl
                    val epObj = currentEp ?: (if (currentEpIndex != -1) allEpisodes.getOrNull(currentEpIndex) else null) ?: currentEpisode
                    DesktopDataStore.saveWatchProgress(
                        WatchProgress(
                            title = title + (activeEpisodeName?.let { if (it.isNotBlank()) " — $it" else "" } ?: ""),
                            url = loadResponse?.url ?: activeSource.url,
                            posterUrl = resolvedPoster,
                            positionMillis = pos,
                            durationMillis = dur,
                            episodeIndex = epObj?.episode,
                            seasonIndex = epObj?.season,
                            apiName = loadResponse?.apiName ?: apiName
                        )
                    )
                }

                // Auto-Play Next Episode when current episode is finished
                if (dur > 30_000L && pos > 0 && (dur - pos) < 2500L && hasNextEpisode && !isExtractingNextEp) {
                    if (currentEpIndex != -1 && currentEpIndex < allEpisodes.size - 1) {
                        playEpisode(allEpisodes[currentEpIndex + 1])
                    }
                }

                // Fetch live decoded audio and subtitle track descriptions
                val audioDescs = player?.audio()?.trackDescriptions() ?: emptyList()
                if (audioDescs.size > 1 && audioTracks != audioDescs) {
                    audioTracks = audioDescs
                    if (selectedAudioTrackId == null) {
                        selectedAudioTrackId = player?.audio()?.track()
                    }
                }

                val subDescs = player?.subpictures()?.trackDescriptions() ?: emptyList()
                if (subDescs.size > 1 && nativeSubTracks != subDescs) {
                    nativeSubTracks = subDescs
                    if (selectedSubTrackId == null) {
                        selectedSubTrackId = player?.subpictures()?.track()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // 20-second timeout
    LaunchedEffect(activeSource.url) {
        delay(20_000)
        if (!isVideoReady && vlcError == null) {
            vlcError = "Stream timed out — server may be slow or URL unavailable"
        }
    }

    // Auto-hide controls after 4s
    LaunchedEffect(showControls, isLocked) {
        if (showControls && isVideoReady && !isLocked) {
            delay(4000)
            showControls = false
        }
    }

    // Seek indicator auto-clear
    LaunchedEffect(seekIndicator) {
        if (seekIndicator != null) {
            delay(1200)
            seekIndicator = null
        }
    }

    // ── Cleanup / Save progress on screen exit ────────────────────────────────
    DisposableEffect(Unit) {
        onDispose {
            try {
                player?.controls()?.stop()
                player?.release()
                factoryAndPlayer?.first?.release()
            } catch (_: Exception) {}
            val resolvedPoster = posterUrl ?: loadResponse?.posterUrl
            val epObj = currentEp ?: (if (currentEpIndex != -1) allEpisodes.getOrNull(currentEpIndex) else null) ?: currentEpisode
            DesktopDataStore.saveWatchProgress(
                WatchProgress(
                    title = title + (activeEpisodeName?.let { if (it.isNotBlank()) " — $it" else "" } ?: (episodeName?.let { " — $it" } ?: "")),
                    url = loadResponse?.url ?: activeSource.url,
                    posterUrl = resolvedPoster,
                    positionMillis = currentPosition,
                    durationMillis = totalDuration,
                    episodeIndex = epObj?.episode,
                    seasonIndex = epObj?.season,
                    apiName = loadResponse?.apiName ?: apiName
                )
            )
        }
    }

    // ── Helper lambdas ────────────────────────────────────────────────────────
    fun seekBy(ms: Long) {
        try {
            val newPos = (currentPosition + ms).coerceIn(0, totalDuration)
            player?.controls()?.setTime(newPos)
            currentPosition = newPos
            seekIndicator = if (ms > 0) "+${ms / 1000}s" else "${ms / 1000}s"
        } catch (_: Exception) {}
    }

    fun togglePlayPause() {
        if (player != null) {
            val nextState = !isPlaying
            isPlaying = nextState
            try {
                if (nextState) player.controls().play()
                else player.controls().pause()
            } catch (_: Exception) {}
        }
    }

    fun setVolume(v: Int) {
        volume = v.coerceIn(0, 200)
        try { player?.audio()?.setVolume(volume) } catch (_: Exception) {}
    }

    fun setSpeed(s: Float) {
        playbackSpeed = s
        try { player?.controls()?.setRate(s) } catch (_: Exception) {}
    }

    fun selectAudioTrack(trackId: Int) {
        selectedAudioTrackId = trackId
        try { player?.audio()?.setTrack(trackId) } catch (_: Exception) {}
    }

    fun selectNativeSubTrack(trackId: Int) {
        selectedSubTrackId = trackId
        try { player?.subpictures()?.setTrack(trackId) } catch (_: Exception) {}
    }

    fun launchExternalVlc() {
        try {
            val vlcPath = findVlcPath()
            val exe = if (vlcPath != null) File(vlcPath, "vlc.exe") else null
            if (exe?.exists() == true) Runtime.getRuntime().exec(arrayOf(exe.absolutePath, activeSource.url))
            else Desktop.getDesktop().browse(URI(activeSource.url))
        } catch (_: Exception) { try { Desktop.getDesktop().browse(URI(activeSource.url)) } catch (_: Exception) {} }
    }

    // ── Render ────────────────────────────────────────────────────────────────
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.F, Key.F11 -> { onToggleFullscreen(); true }
                        Key.Escape -> {
                            if (isFullscreen) { onToggleFullscreen(); true }
                            else false
                        }
                        Key.Spacebar -> { togglePlayPause(); true }
                        Key.DirectionRight -> { seekBy(10_000L); true }
                        Key.DirectionLeft -> { seekBy(-10_000L); true }
                        Key.M -> {
                            isMuted = !isMuted
                            try { player?.audio()?.isMute = isMuted } catch (_: Exception) {}
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .combinedClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {
                    if (!isLocked) showControls = !showControls
                    else showControls = true
                },
                onDoubleClick = {
                    onToggleFullscreen()
                }
            )
    ) {
        // Video bitmap or fallback/connecting states
        when {
            vlcError != null -> FallbackDashboard(
                mediaUrl = activeSource.url, title = title, episodeName = episodeName,
                errorMsg = vlcError!!, isCopied = isCopied,
                onCopy = { clipboardManager.setText(AnnotatedString(activeSource.url)); isCopied = true },
                onLaunchVlc = { launchExternalVlc() },
                onOpenBrowser = { try { Desktop.getDesktop().browse(URI(activeSource.url)) } catch (_: Exception) {} }
            )
            isExtractingNextEp -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F0F12)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(18.dp))
                    Text("Fetching stream sources…", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Connecting to provider link", color = Color.White.copy(0.5f), fontSize = 12.sp)
                }
            }
            videoFrame == null || !isVideoReady -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0D0D10)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(18.dp))
                    Text("Connecting to ${activeSource.name}…", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Buffering initial stream segments", color = Color.White.copy(0.5f), fontSize = 12.sp)
                }
            }
            else -> Image(
                bitmap = videoFrame!!,
                contentDescription = "Video",
                modifier = Modifier.fillMaxSize(),
                contentScale = videoAspectMode.scale
            )
        }

        // Seek indicator (centre flash)
        seekIndicator?.let { label ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Buffering spinner overlay
        if (isBuffering && videoFrame != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp), strokeWidth = 3.dp)
            }
        }

        // Controls overlay
        AnimatedVisibility(
            visible = showControls || videoFrame == null,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLocked) {
                LockedOverlay(onUnlock = { isLocked = false })
            } else {
                PlayerControlsOverlay(
                    title = title,
                    episodeName = activeEpisodeName,
                    activeSourceName = activeSource.name,
                    isPlaying = isPlaying,
                    isMuted = isMuted,
                    volume = volume,
                    playbackSpeed = playbackSpeed,
                    currentPosition = currentPosition,
                    totalDuration = totalDuration,
                    hasEpisodes = allEpisodes.isNotEmpty(),
                    hasPrevEpisode = hasPrevEpisode,
                    hasNextEpisode = hasNextEpisode,
                    hasSources = activeSourcesList.size > 1,
                    hasAudioTracks = audioTracks.isNotEmpty(),
                    hasSubtitles = activeSubtitlesList.isNotEmpty() || nativeSubTracks.isNotEmpty(),
                    hasVideo = videoFrame != null && isVideoReady && !isExtractingNextEp,
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = onToggleFullscreen,
                    videoAspectMode = videoAspectMode,
                    onToggleAspectMode = {
                        videoAspectMode = when (videoAspectMode) {
                            VideoAspectMode.CROP -> VideoAspectMode.STRETCH
                            VideoAspectMode.STRETCH -> VideoAspectMode.FIT
                            VideoAspectMode.FIT -> VideoAspectMode.CROP
                        }
                    },
                    onBack = onBack,
                    onPlayPause = { togglePlayPause() },
                    onSeekBack30 = { seekBy(-30_000) },
                    onSeekForward30 = { seekBy(30_000) },
                    onSeekBack10 = { seekBy(-10_000) },
                    onSeekForward10 = { seekBy(10_000) },
                    onPrevEpisode = { if (hasPrevEpisode) playEpisode(allEpisodes[currentEpIndex - 1]) },
                    onNextEpisode = { if (hasNextEpisode) playEpisode(allEpisodes[currentEpIndex + 1]) },
                    onSeek = { pct ->
                        val seekTo = (pct * totalDuration).toLong()
                        currentPosition = seekTo
                        try { player?.controls()?.setTime(seekTo) } catch (_: Exception) {}
                    },
                    onMuteToggle = {
                        isMuted = !isMuted
                        try { player?.audio()?.isMute = isMuted } catch (_: Exception) {}
                    },
                    onVolumeClick = { showVolumePicker = true },
                    onSpeedClick = { showSpeedPicker = true },
                    onEpisodesClick = { showEpisodesPicker = true },
                    onSourcesClick = { showSourcesPicker = true },
                    onAudioTracksClick = { showAudioTrackPicker = true },
                    onSubtitleClick = { showSubtitlePicker = true },
                    onLock = { isLocked = true; showControls = false },
                    onExternalVlc = { launchExternalVlc() }
                )
            }
        }

        // ── Sidebar Drawers (smooth fade-in background scrim + slide-in sidebar) ──
        if (allEpisodes.isNotEmpty()) {
            EpisodesPickerDialog(
                visible = showEpisodesPicker,
                episodes = allEpisodes,
                currentEpisodeIndex = currentEpIndex,
                onSelect = { ep ->
                    playEpisode(ep)
                    showEpisodesPicker = false
                },
                onDismiss = { showEpisodesPicker = false }
            )
        }

        if (activeSourcesList.isNotEmpty()) {
            SourcesPickerDialog(
                visible = showSourcesPicker,
                sources = activeSourcesList,
                currentSource = activeSource,
                onSelect = { src ->
                    activeSource = src
                    vlcError = null
                    isVideoReady = false
                    showSourcesPicker = false
                },
                onDismiss = { showSourcesPicker = false }
            )
        }

        AudioTrackPickerDialog(
            visible = showAudioTrackPicker,
            tracks = audioTracks,
            selectedTrackId = selectedAudioTrackId,
            onSelect = { id -> selectAudioTrack(id); showAudioTrackPicker = false },
            onDismiss = { showAudioTrackPicker = false }
        )

        CombinedSubtitlePickerDialog(
            visible = showSubtitlePicker,
            externalSubtitles = activeSubtitlesList,
            selectedExternal = selectedSubtitle,
            nativeTracks = nativeSubTracks,
            selectedNativeId = selectedSubTrackId,
            onSelectExternal = { sub -> selectedSubtitle = sub; showSubtitlePicker = false },
            onSelectNative = { id -> selectNativeSubTrack(id); showSubtitlePicker = false },
            onDismiss = { showSubtitlePicker = false }
        )

        SpeedPickerDialog(
            visible = showSpeedPicker,
            current = playbackSpeed,
            onSelect = { setSpeed(it); showSpeedPicker = false },
            onDismiss = { showSpeedPicker = false }
        )

        VolumeDialog(
            visible = showVolumePicker,
            volume = volume,
            isMuted = isMuted,
            onVolumeChange = { setVolume(it) },
            onMuteToggle = {
                isMuted = !isMuted
                try { player?.audio()?.isMute = isMuted } catch (_: Exception) {}
            },
            onDismiss = { showVolumePicker = false }
        )
    }
}

// ─── Full Controls Overlay ────────────────────────────────────────────────────

@Composable
private fun PlayerControlsOverlay(
    title: String,
    episodeName: String?,
    activeSourceName: String,
    isPlaying: Boolean,
    isMuted: Boolean,
    volume: Int,
    playbackSpeed: Float,
    currentPosition: Long,
    totalDuration: Long,
    hasEpisodes: Boolean,
    hasPrevEpisode: Boolean,
    hasNextEpisode: Boolean,
    hasSources: Boolean,
    hasAudioTracks: Boolean,
    hasSubtitles: Boolean,
    hasVideo: Boolean,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    videoAspectMode: VideoAspectMode,
    onToggleAspectMode: () -> Unit,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBack30: () -> Unit,
    onSeekForward30: () -> Unit,
    onSeekBack10: () -> Unit,
    onSeekForward10: () -> Unit,
    onPrevEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onSeek: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onVolumeClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onEpisodesClick: () -> Unit,
    onSourcesClick: () -> Unit,
    onAudioTracksClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onLock: () -> Unit,
    onExternalVlc: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Top gradient fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(0.85f), Color.Transparent)))
        )
        // Bottom gradient fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.90f))))
        )

        // ── TOP BAR ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ControlIconButton(Icons.Default.ArrowBack, "Back", onClick = onBack)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        title, color = Color.White, fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (episodeName != null) {
                            Text(episodeName, color = Color.White.copy(0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("  •  ", color = Color.White.copy(0.4f), fontSize = 12.sp)
                        }
                        Text(cleanServerTitle(activeSourceName), color = Color(0xFF0078D4), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Episodes Selector Button
                if (hasEpisodes) {
                    Surface(
                        onClick = onEpisodesClick,
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF2C2C2C),
                        border = BorderStroke(1.dp, Color(0x18FFFFFF)),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.FormatListBulleted, null, modifier = Modifier.size(14.dp), tint = Color.White)
                            Text("Episodes", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Sources / Mirrors Button
                if (hasSources) {
                    Surface(
                        onClick = onSourcesClick,
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF2C2C2C),
                        border = BorderStroke(1.dp, Color(0x18FFFFFF)),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Dns, null, modifier = Modifier.size(14.dp), tint = Color.White)
                            Text("Sources", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Speed badge
                if (playbackSpeed != 1.0f) {
                    Surface(color = Color(0xFF0078D4), shape = RoundedCornerShape(4.dp)) {
                        Text("${playbackSpeed}x", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ── CENTRE CONTROLS ──────────────────────────────────────────────────
        if (hasVideo) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasEpisodes) {
                    IconButton(onClick = onPrevEpisode, enabled = hasPrevEpisode, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.Default.SkipPrevious, null,
                            tint = if (hasPrevEpisode) Color.White else Color.White.copy(0.3f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                IconButton(onClick = onSeekBack30, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Replay30, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }

                // Play / Pause central button
                Surface(
                    onClick = onPlayPause,
                    shape = CircleShape,
                    color = Color.Black.copy(0.55f),
                    border = BorderStroke(1.dp, Color.White.copy(0.3f)),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null, tint = Color.White, modifier = Modifier.size(34.dp)
                        )
                    }
                }

                IconButton(onClick = onSeekForward30, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Forward30, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }

                if (hasEpisodes) {
                    IconButton(onClick = onNextEpisode, enabled = hasNextEpisode, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.Default.SkipNext, null,
                            tint = if (hasNextEpisode) Color.White else Color.White.copy(0.3f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        // ── BOTTOM BAR ───────────────────────────────────────────────────────
        if (hasVideo) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                // Seekbar with time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(formatTime(currentPosition), color = Color.White.copy(0.8f), fontSize = 12.sp)
                    Slider(
                        value = if (totalDuration > 0) (currentPosition.toFloat() / totalDuration).coerceIn(0f, 1f) else 0f,
                        onValueChange = onSeek,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color(0xFF0078D4),
                            inactiveTrackColor = Color.White.copy(0.25f)
                        )
                    )
                    Text(formatTime(totalDuration), color = Color.White.copy(0.8f), fontSize = 12.sp)
                }

                // Controls row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        ControlIconButton(Icons.Default.Replay10, "Replay 10s", onClick = onSeekBack10)
                        ControlIconButton(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            "Play/Pause", onClick = onPlayPause
                        )
                        ControlIconButton(Icons.Default.Forward10, "Forward 10s", onClick = onSeekForward10)
                        Spacer(Modifier.width(6.dp))
                        ControlIconButton(
                            if (isMuted || volume == 0) Icons.Default.VolumeOff
                            else if (volume < 80) Icons.Default.VolumeDown
                            else Icons.Default.VolumeUp,
                            "Volume",
                            onClick = onVolumeClick
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (hasSources) {
                            ControlIconButton(Icons.Default.Dns, "Mirrors & Sources", onClick = onSourcesClick)
                        }
                        if (hasAudioTracks) {
                            ControlIconButton(Icons.Default.Audiotrack, "Audio Tracks", onClick = onAudioTracksClick)
                        }
                        Surface(
                            onClick = onSpeedClick,
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Transparent,
                            modifier = Modifier.height(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text("${playbackSpeed}x", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (hasSubtitles) {
                            ControlIconButton(Icons.Default.ClosedCaption, "Subtitles", onClick = onSubtitleClick)
                        }
                        ControlIconButton(Icons.Default.Lock, "Lock controls", onClick = onLock)
                        ControlIconButton(
                            Icons.Default.AspectRatio,
                            "Aspect Mode: ${videoAspectMode.label}",
                            onClick = onToggleAspectMode
                        )
                        ControlIconButton(
                            if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            if (isFullscreen) "Exit Fullscreen" else "Fullscreen",
                            onClick = onToggleFullscreen
                        )
                    }
                }
            }
        }
    }
}

// ─── Locked Overlay ───────────────────────────────────────────────────────────

@Composable
private fun LockedOverlay(onUnlock: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        IconButton(
            onClick = onUnlock,
            modifier = Modifier
                .padding(20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(0.5f))
                .size(48.dp)
        ) {
            Icon(Icons.Default.LockOpen, "Unlock", tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

// ─── Sources & Mirrors Picker Dialog ──────────────────────────────────────────

private fun cleanServerTitle(rawName: String): String {
    val tokens = rawName.split("|", "•").map { it.trim() }.filter { it.isNotBlank() }
    val seen = mutableSetOf<String>()
    val result = mutableListOf<String>()
    for (t in tokens) {
        val norm = t.lowercase().replace(" ", "")
        if (seen.add(norm)) {
            result.add(t)
        }
    }
    val cleaned = result.joinToString(" • ")
    return if (cleaned.isBlank()) rawName else cleaned
}

private fun detectQualityLabel(source: StreamSourceInfo): String {
    val text = (source.name + " " + source.url).lowercase()
    return when {
        source.quality >= 2160 || text.contains("4k") || text.contains("2160") -> "4K"
        source.quality >= 1080 || text.contains("1080") -> "1080p"
        source.quality >= 720 || text.contains("720") -> "720p"
        source.quality >= 480 || text.contains("480") -> "480p"
        else -> "HD"
    }
}

// ─── Reusable Full Height Right Sidebar Drawer Container ───────────────────────

@Composable
private fun RightSidebarDrawer(
    visible: Boolean,
    onDismiss: () -> Unit,
    width: Dp = 440.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(180, easing = FastOutSlowInEasing)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.65f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss
                )
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                ),
                exit = slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                ),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(width)
                    .align(Alignment.CenterEnd)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = {}
                        ),
                    color = Color(0xFF242424).copy(0.98f),
                    border = BorderStroke(1.dp, Color(0x18FFFFFF))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp)
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

// ─── Sources Picker Sidebar ───────────────────────────────────────────────────

// ─── Sources Picker Sidebar ───────────────────────────────────────────────────

@Composable
private fun SourcesPickerDialog(
    visible: Boolean,
    sources: List<StreamSourceInfo>,
    currentSource: StreamSourceInfo,
    onSelect: (StreamSourceInfo) -> Unit,
    onDismiss: () -> Unit
) {
    RightSidebarDrawer(visible = visible, onDismiss = onDismiss, width = 460.dp) {
        // Aligned Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Dns, null,
                    tint = Color(0xFF0078D4),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Stream Sources",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                    Text(
                        "${sources.size} mirror server${if (sources.size != 1) "s" else ""} available",
                        color = Color.White.copy(0.6f),
                        fontSize = 12.sp
                    )
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Color.White.copy(0.7f), modifier = Modifier.size(18.dp))
            }
        }

        HorizontalDivider(color = Color.White.copy(0.08f))
        Spacer(Modifier.height(14.dp))

        // Full Height Scrollable Item List
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sources.forEach { source ->
                val isSelected = source.url == currentSource.url || source == currentSource
                val qualityLabel = detectQualityLabel(source)
                val cleanedTitle = cleanServerTitle(source.name)

                Win11SidebarOptionCard(
                    isSelected = isSelected,
                    onClick = { onSelect(source) }
                ) {
                    // Quality Badge Pill
                    val (badgeBg, badgeTextColor) = when (qualityLabel) {
                        "4K" -> Pair(Color(0xFFD97706), Color.White)
                        "1080p" -> Pair(Color(0xFF0078D4), Color.White)
                        "720p" -> Pair(Color(0xFF107C41), Color.White)
                        else -> Pair(Color(0xFF3F3F46), Color.White)
                    }
                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            qualityLabel,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            color = badgeTextColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Title & Domain URL
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            cleanedTitle,
                            color = Color.White,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            source.url,
                            color = Color.White.copy(0.5f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Selection Checkmark
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            null,
                            tint = Color(0xFF0078D4),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─── Audio Track Picker Sidebar ───────────────────────────────────────────────

private fun cleanAudioTrackName(rawName: String): String {
    val name = rawName.trim()
    val bracketMatch = Regex("""\[(.*?)\]""").find(name)
    if (bracketMatch != null) {
        val lang = bracketMatch.groupValues[1].trim()
        if (lang.isNotBlank()) return "$lang Audio"
    }
    return name.ifBlank { "Audio Track" }
}

@Composable
private fun AudioTrackPickerDialog(
    visible: Boolean,
    tracks: List<TrackDescription>,
    selectedTrackId: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    RightSidebarDrawer(visible = visible, onDismiss = onDismiss, width = 420.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Audiotrack, null,
                    tint = Color(0xFF0078D4),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Audio Track",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                    Text(
                        "Select active audio stream language",
                        color = Color.White.copy(0.6f),
                        fontSize = 12.sp
                    )
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Color.White.copy(0.7f), modifier = Modifier.size(18.dp))
            }
        }

        HorizontalDivider(color = Color.White.copy(0.08f))
        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tracks.forEach { track ->
                val isSelected = track.id() == selectedTrackId
                val cleanName = cleanAudioTrackName(track.description())

                Win11SidebarOptionCard(
                    isSelected = isSelected,
                    onClick = { onSelect(track.id()) }
                ) {
                    Text(
                        cleanName,
                        color = Color.White,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            null,
                            tint = Color(0xFF0078D4),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─── Combined Subtitle Picker Sidebar ──────────────────────────────────────────

@Composable
private fun CombinedSubtitlePickerDialog(
    visible: Boolean,
    externalSubtitles: List<SubtitleTrackInfo>,
    selectedExternal: SubtitleTrackInfo?,
    nativeTracks: List<TrackDescription>,
    selectedNativeId: Int?,
    onSelectExternal: (SubtitleTrackInfo?) -> Unit,
    onSelectNative: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    RightSidebarDrawer(visible = visible, onDismiss = onDismiss, width = 440.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ClosedCaption, null,
                    tint = Color(0xFF0078D4),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Subtitles",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                    Text(
                        "Select subtitles track or embedded stream",
                        color = Color.White.copy(0.6f),
                        fontSize = 12.sp
                    )
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Color.White.copy(0.7f), modifier = Modifier.size(18.dp))
            }
        }

        HorizontalDivider(color = Color.White.copy(0.08f))
        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SubtitleOption("Off", selectedExternal == null && (selectedNativeId == null || selectedNativeId == -1), onClick = {
                onSelectExternal(null)
                onSelectNative(-1)
            })

            if (nativeTracks.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Embedded Subtitles", color = Color(0xFF0078D4), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                nativeTracks.filter { it.id() != -1 }.forEach { track ->
                    val isSelected = track.id() == selectedNativeId
                    SubtitleOption(cleanAudioTrackName(track.description()), isSelected, onClick = { onSelectNative(track.id()) })
                }
            }

            if (externalSubtitles.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("External Subtitles", color = Color(0xFF0078D4), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                externalSubtitles.forEach { track ->
                    val isSelected = track == selectedExternal
                    SubtitleOption(
                        track.language.ifBlank { track.url.substringAfterLast('/') },
                        isSelected,
                        onClick = { onSelectExternal(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodesPickerDialog(
    visible: Boolean,
    episodes: List<Episode>,
    currentEpisodeIndex: Int,
    onSelect: (Episode) -> Unit,
    onDismiss: () -> Unit
) {
    val seasons = remember(episodes) { episodes.mapNotNull { it.season }.distinct().sorted() }
    var selectedSeason by remember { mutableStateOf(episodes.getOrNull(currentEpisodeIndex)?.season ?: seasons.firstOrNull() ?: 1) }

    val seasonEpisodes = remember(episodes, selectedSeason) {
        if (seasons.size > 1) {
            episodes.filter { it.season == selectedSeason || (it.season == null && selectedSeason == 1) }
        } else episodes
    }

    RightSidebarDrawer(visible = visible, onDismiss = onDismiss, width = 480.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.VideoLibrary, null,
                    tint = Color(0xFF0078D4),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Episodes",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                    Text(
                        "${episodes.size} episode${if (episodes.size != 1) "s" else ""} available",
                        color = Color.White.copy(0.6f),
                        fontSize = 12.sp
                    )
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Color.White.copy(0.7f), modifier = Modifier.size(18.dp))
            }
        }

        HorizontalDivider(color = Color.White.copy(0.08f))

        if (seasons.size > 1) {
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(seasons) { season ->
                    val isSelected = selectedSeason == season
                    val textColor by animateColorAsState(if (isSelected) Color.White else Color.White.copy(0.6f), tween(150))

                    Surface(
                        onClick = { selectedSeason = season },
                        shape = RoundedCornerShape(5.dp),
                        color = if (isSelected) Color(0xFF0078D4).copy(0.15f) else Color.Transparent,
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF0078D4).copy(0.5f) else Color(0x18FFFFFF))
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

        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            seasonEpisodes.forEach { ep ->
                val isSelected = episodes.getOrNull(currentEpisodeIndex)?.data == ep.data
                val epLabel = "S${ep.season ?: 1}:E${ep.episode ?: (seasonEpisodes.indexOf(ep) + 1)}"

                Win11SidebarOptionCard(
                    isSelected = isSelected,
                    onClick = { onSelect(ep) }
                ) {
                    Surface(
                        color = if (isSelected) Color(0xFF0078D4) else Color(0xFF3F3F46),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            epLabel,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            ep.name ?: epLabel,
                            color = Color.White,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        ep.description?.let { desc ->
                            if (desc.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    desc,
                                    color = Color.White.copy(0.5f),
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (isSelected) {
                        Icon(
                            Icons.Default.Check, null,
                            tint = Color(0xFF0078D4),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleOption(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Win11SidebarOptionCard(
        isSelected = isSelected,
        onClick = onClick
    ) {
        Text(
            label,
            color = Color.White,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                null,
                tint = Color(0xFF0078D4),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ─── Windows 11 Option Card Component ─────────────────────────────────────────

@Composable
private fun Win11SidebarOptionCard(
    isSelected: Boolean,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (isSelected) Color(0xFF2C2C2C) else if (hovered) Color(0xFF323232) else Color(0xFF242424),
        border = BorderStroke(
            1.dp,
            if (isSelected) Color(0xFF0078D4).copy(0.5f) else if (hovered) Color(0x26FFFFFF) else Color(0x18FFFFFF)
        ),
        modifier = Modifier.fillMaxWidth().hoverable(interactionSource)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Windows 11 Accent Left Bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(
                        if (isSelected) Color(0xFF0078D4) else Color.Transparent,
                        shape = RoundedCornerShape(2.dp)
                    )
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

// ─── Speed Picker Sidebar ─────────────────────────────────────────────────────

@Composable
private fun SpeedPickerDialog(visible: Boolean, current: Float, onSelect: (Float) -> Unit, onDismiss: () -> Unit) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 3.0f)
    RightSidebarDrawer(visible = visible, onDismiss = onDismiss, width = 360.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Playback Speed", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Color.White.copy(0.7f), modifier = Modifier.size(18.dp))
            }
        }
        HorizontalDivider(color = Color.White.copy(0.08f))
        Spacer(Modifier.height(14.dp))
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            speeds.forEach { speed ->
                val isSelected = speed == current
                SubtitleOption("${speed}x${if (speed == 1.0f) " (Normal)" else ""}", isSelected, onClick = { onSelect(speed) })
            }
        }
    }
}

// ─── Volume Sidebar ───────────────────────────────────────────────────────────

@Composable
private fun VolumeDialog(
    visible: Boolean,
    volume: Int, isMuted: Boolean,
    onVolumeChange: (Int) -> Unit,
    onMuteToggle: () -> Unit,
    onDismiss: () -> Unit
) {
    RightSidebarDrawer(visible = visible, onDismiss = onDismiss, width = 360.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Volume", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("${volume}%", color = Color(0xFF0078D4), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Color.White.copy(0.7f), modifier = Modifier.size(18.dp))
            }
        }
        HorizontalDivider(color = Color.White.copy(0.08f))
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMuteToggle) {
                Icon(if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, null, tint = if (isMuted) Color(0xFFCF6679) else Color.White)
            }
            Slider(
                value = volume.toFloat(),
                onValueChange = { onVolumeChange(it.toInt()) },
                valueRange = 0f..200f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF0078D4),
                    inactiveTrackColor = Color.White.copy(0.15f)
                )
            )
        }
        if (volume > 100) {
            Text("⚠ Volume above 100% may distort audio", color = Color(0xFFFFB300), fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        }
    }
}

// ─── Fallback Dashboard ───────────────────────────────────────────────────────

@Composable
private fun FallbackDashboard(
    mediaUrl: String, title: String, episodeName: String?,
    errorMsg: String, isCopied: Boolean,
    onCopy: () -> Unit, onLaunchVlc: () -> Unit, onOpenBrowser: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0A0A0F), Color(0xFF12121A)))),
        contentAlignment = Alignment.Center
    ) {
        Card(modifier = Modifier.fillMaxWidth(0.7f), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2A)), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PlayCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(80.dp))
                Spacer(Modifier.height(16.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = Color.White, textAlign = TextAlign.Center)
                if (episodeName != null) Text(episodeName, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text("VLC: $errorMsg", color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF141420), shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Link, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(mediaUrl, color = Color.LightGray, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = onCopy) {
                            Icon(if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onLaunchVlc, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Launch VLC")
                    }
                    OutlinedButton(onClick = onOpenBrowser, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                        Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Browser")
                    }
                }
            }
        }
    }
}

// ─── Helper ───────────────────────────────────────────────────────────────────

@Composable
private fun ControlIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 20.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (hovered) Color.White.copy(alpha = 0.12f) else Color.Transparent,
        modifier = Modifier.size(32.dp).hoverable(interactionSource)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, desc, tint = Color.White, modifier = Modifier.size(size))
        }
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000) % 60; val m = (ms / 60000) % 60; val h = ms / 3600000
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
