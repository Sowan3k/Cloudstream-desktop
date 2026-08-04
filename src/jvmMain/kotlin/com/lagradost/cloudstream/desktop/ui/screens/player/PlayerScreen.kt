package com.lagradost.cloudstream.desktop.ui.screens.player

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lagradost.cloudstream.desktop.storage.DesktopDataStore
import com.lagradost.cloudstream.desktop.storage.WatchProgress
import com.lagradost.cloudstream.desktop.ui.navigation.StreamSourceInfo
import com.lagradost.cloudstream.desktop.ui.navigation.SubtitleTrackInfo
import com.lagradost.cloudstream3.LoadResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    // LibVLC expects an ASCII MRL. URI.toASCIIString() preserves an already
    // encoded URL while encoding spaces and Unicode path characters such as ’.
    URI(url.replace(" ", "%20")).toASCIIString()
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
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var isCopied by remember { mutableStateOf(false) }

    // ── Active Stream Source ──────────────────────────────────────────────────
    var activeSource by remember {
        mutableStateOf(
            sources.firstOrNull { it.url == mediaUrl }
                ?: StreamSourceInfo(name = "Primary Stream", url = mediaUrl, quality = 0, headers = headers)
        )
    }

    var videoAspectMode by remember { mutableStateOf(VideoAspectMode.CROP) }
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var volume by remember { mutableStateOf(100) }          // 0..200
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var currentPosition by remember { mutableStateOf(0L) }
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
    var selectedSubtitle by remember { mutableStateOf<SubtitleTrackInfo?>(subtitles.firstOrNull()) }
    var seekIndicator by remember { mutableStateOf<String?>(null) }

    // ── Native Decoded Tracks ──────────────────────────────────────────────────
    var audioTracks by remember { mutableStateOf<List<TrackDescription>>(emptyList()) }
    var nativeSubTracks by remember { mutableStateOf<List<TrackDescription>>(emptyList()) }
    var selectedAudioTrackId by remember { mutableStateOf<Int?>(null) }
    var selectedSubTrackId by remember { mutableStateOf<Int?>(null) }

    // ── Lock-free Atomic Frame Buffer & Frame Ticker ──────────────────────────
    class RawFrameData(val pixels: IntArray, val width: Int, val height: Int)
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

    // 60fps Frame Ticker — consumes latest frame without EDT queue backlog
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
                } catch (_: Exception) {}
            }
            delay(16)
        }
    }

    // ── Zero-GC Pooled Frame Buffer ───────────────────────────────────────────
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
                add("--avcodec-threads=0")       // Auto multi-threaded CPU decoding fallback
                add("--network-caching=5000")    // 5-second network buffer for high-bitrate 25GB 4K streams
                add("--file-caching=5000")       // 5-second file buffer
                add("--http-reconnect")
                add("--no-stats")
            }
            val factory = MediaPlayerFactory(*args.toTypedArray())
            val player = factory.mediaPlayers().newEmbeddedMediaPlayer()

            val bufferFmt = object : BufferFormatCallback {
                override fun getBufferFormat(width: Int, height: Int): BufferFormat {
                    // Cap max pixel buffer to 1920x1080 for 4K streams (prevents 2GB/s memory copy bottleneck)
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
                            val pixels = IntArray(count)
                            ints.get(pixels, 0, count)
                            pendingRawFrame.set(RawFrameData(pixels, fw, fh))
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
                    javax.swing.SwingUtilities.invokeLater { isBuffering = newCache < 100f }
                }
                override fun playing(mp: MediaPlayer) {
                    javax.swing.SwingUtilities.invokeLater { isBuffering = false }
                }
                override fun stopped(mp: MediaPlayer) {
                    javax.swing.SwingUtilities.invokeLater { isVideoReady = false }
                }
            })

            Pair(factory, player)
        } catch (e: Exception) {
            vlcError = e.message ?: "Failed to initialize VLC"
            null
        }
    }

    val player = factoryAndPlayer?.second

    // ── Smooth Server / Source Switching ──────────────────────────────────────
    var isFirstPlay by remember { mutableStateOf(true) }
    LaunchedEffect(activeSource.url) {
        if (player != null) {
            val savedPos = currentPosition
            vlcError = null
            pendingRawFrame.set(null)
            isBuffering = true

            val safeUrl = toVlcMrl(activeSource.url)
            val opts = buildList {
                activeSource.headers.forEach { (k, v) -> add(":http-header-$k=$v") }
                add(":network-caching=3000"); add(":http-reconnect")
            }.toTypedArray()

            try {
                logVlc("Starting LibVLC with MRL: $safeUrl")
                player.media().play(safeUrl, *opts)
                if (!isFirstPlay && savedPos > 1000L) {
                    delay(600)
                    try { player.controls().setTime(savedPos) } catch (_: Exception) {}
                }
                isFirstPlay = false
            } catch (e: Exception) {
                vlcError = "Failed to switch source: ${e.message}"
                isBuffering = false
            }
        }
    }

    // ── Polling & Track Sync ───────────────────────────────────────────────────
    LaunchedEffect(player) {
        while (isActive) {
            delay(500)
            try {
                val dur = player?.status()?.length() ?: 0L
                val pos = player?.status()?.time() ?: 0L
                if (dur > 0) { totalDuration = dur; currentPosition = pos }

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
            DesktopDataStore.saveWatchProgress(
                WatchProgress(
                    title = title + (episodeName?.let { " — $it" } ?: ""),
                    url = loadResponse?.url ?: activeSource.url,
                    posterUrl = resolvedPoster,
                    positionMillis = currentPosition,
                    durationMillis = totalDuration
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
        isPlaying = !isPlaying
        try { if (isPlaying) player?.controls()?.play() else player?.controls()?.pause() } catch (_: Exception) {}
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

    // ── Dialogs ───────────────────────────────────────────────────────────────
    if (showSourcesPicker && sources.isNotEmpty()) {
        SourcesPickerDialog(
            sources = sources,
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

    if (showAudioTrackPicker) {
        AudioTrackPickerDialog(
            tracks = audioTracks,
            selectedTrackId = selectedAudioTrackId,
            onSelect = { id -> selectAudioTrack(id); showAudioTrackPicker = false },
            onDismiss = { showAudioTrackPicker = false }
        )
    }

    if (showSubtitlePicker) {
        CombinedSubtitlePickerDialog(
            externalSubtitles = subtitles,
            selectedExternal = selectedSubtitle,
            nativeTracks = nativeSubTracks,
            selectedNativeId = selectedSubTrackId,
            onSelectExternal = { sub -> selectedSubtitle = sub; showSubtitlePicker = false },
            onSelectNative = { id -> selectNativeSubTrack(id); showSubtitlePicker = false },
            onDismiss = { showSubtitlePicker = false }
        )
    }

    if (showSpeedPicker) {
        SpeedPickerDialog(
            current = playbackSpeed,
            onSelect = { setSpeed(it); showSpeedPicker = false },
            onDismiss = { showSpeedPicker = false }
        )
    }

    if (showVolumePicker) {
        VolumeDialog(
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
            videoFrame != null -> Image(
                bitmap = videoFrame!!,
                contentDescription = "Video",
                modifier = Modifier.fillMaxSize(),
                contentScale = videoAspectMode.scale
            )
            vlcError != null -> FallbackDashboard(
                mediaUrl = activeSource.url, title = title, episodeName = episodeName,
                errorMsg = vlcError!!, isCopied = isCopied,
                onCopy = { clipboardManager.setText(AnnotatedString(activeSource.url)); isCopied = true },
                onLaunchVlc = { launchExternalVlc() },
                onOpenBrowser = { try { Desktop.getDesktop().browse(URI(activeSource.url)) } catch (_: Exception) {} }
            )
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("Connecting to ${activeSource.name}…", color = Color.Gray)
                }
            }
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
                    episodeName = episodeName,
                    activeSourceName = activeSource.name,
                    isPlaying = isPlaying,
                    isMuted = isMuted,
                    volume = volume,
                    playbackSpeed = playbackSpeed,
                    currentPosition = currentPosition,
                    totalDuration = totalDuration,
                    hasSources = sources.size > 1,
                    hasAudioTracks = audioTracks.isNotEmpty(),
                    hasSubtitles = subtitles.isNotEmpty() || nativeSubTracks.isNotEmpty(),
                    hasVideo = videoFrame != null,
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
                    onSourcesClick = { showSourcesPicker = true },
                    onAudioTracksClick = { showAudioTrackPicker = true },
                    onSubtitleClick = { showSubtitlePicker = true },
                    onLock = { isLocked = true; showControls = false },
                    onExternalVlc = { launchExternalVlc() }
                )
            }
        }
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
    onSeek: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onVolumeClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onSourcesClick: () -> Unit,
    onAudioTracksClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onLock: () -> Unit,
    onExternalVlc: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Top gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(0.85f), Color.Transparent)))
        )
        // Bottom gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.90f))))
        )

        // ── TOP BAR ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        title, color = Color.White, fontWeight = FontWeight.Bold,
                        fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (episodeName != null) {
                            Text(episodeName, color = Color.LightGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(" • ", color = Color.Gray, fontSize = 12.sp)
                        }
                        Text(cleanServerTitle(activeSourceName), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Sources / Mirrors Button
                if (hasSources) {
                    OutlinedButton(
                        onClick = onSourcesClick,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(0.3f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Dns, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sources", fontSize = 12.sp)
                    }
                }

                // Speed badge
                if (playbackSpeed != 1.0f) {
                    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(6.dp)) {
                        Text("${playbackSpeed}x", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(4.dp))
                }

                IconButton(onClick = onExternalVlc) {
                    Icon(Icons.Default.OpenInNew, null, tint = Color.White.copy(0.8f))
                }
            }
        }

        // ── CENTRE: Play/Pause + ±30s ─────────────────────────────────────────
        if (hasVideo) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // -30s
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onSeekBack30, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Default.Replay30, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }

                // Play/Pause big
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable { onPlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        null, tint = Color.White, modifier = Modifier.size(42.dp)
                    )
                }

                // +30s
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onSeekForward30, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Default.Forward30, null, tint = Color.White, modifier = Modifier.size(36.dp))
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
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Seekbar with time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(formatTime(currentPosition), color = Color.White, fontSize = 12.sp)
                    Slider(
                        value = if (totalDuration > 0) (currentPosition.toFloat() / totalDuration).coerceIn(0f, 1f) else 0f,
                        onValueChange = onSeek,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(0.35f)
                        )
                    )
                    Text(formatTime(totalDuration), color = Color.White, fontSize = 12.sp)
                }

                // Controls row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: -10s | play/pause | +10s | volume
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ControlIconButton(Icons.Default.Replay10, "Replay 10s", onClick = onSeekBack10)
                        ControlIconButton(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            "Play/Pause", onClick = onPlayPause, size = 30.dp
                        )
                        ControlIconButton(Icons.Default.Forward10, "Forward 10s", onClick = onSeekForward10)
                        Spacer(Modifier.width(8.dp))
                        // Volume
                        ControlIconButton(
                            if (isMuted || volume == 0) Icons.Default.VolumeOff
                            else if (volume < 80) Icons.Default.VolumeDown
                            else Icons.Default.VolumeUp,
                            "Volume",
                            onClick = onVolumeClick
                        )
                    }

                    // Right: sources | audio tracks | speed | subtitles | lock
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Sources button
                        if (hasSources) {
                            ControlIconButton(Icons.Default.Dns, "Mirrors & Sources", onClick = onSourcesClick)
                        }
                        // Audio tracks button
                        if (hasAudioTracks) {
                            ControlIconButton(Icons.Default.Audiotrack, "Audio Tracks", onClick = onAudioTracksClick)
                        }
                        // Speed
                        TextButton(onClick = onSpeedClick) {
                            Text("${playbackSpeed}x", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        // Subtitles
                        if (hasSubtitles) {
                            ControlIconButton(Icons.Default.ClosedCaption, "Subtitles", onClick = onSubtitleClick)
                        }
                        // Lock
                        ControlIconButton(Icons.Default.Lock, "Lock controls", onClick = onLock)
                        // Aspect Ratio (Zoom / Stretch / Fit)
                        ControlIconButton(
                            Icons.Default.AspectRatio,
                            "Aspect Mode: ${videoAspectMode.label}",
                            onClick = onToggleAspectMode
                        )
                        // Fullscreen
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
    val tokens = rawName.split("|", "•", "-").map { it.trim() }.filter { it.isNotBlank() }
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

@Composable
private fun SourcesPickerDialog(
    sources: List<StreamSourceInfo>,
    currentSource: StreamSourceInfo,
    onSelect: (StreamSourceInfo) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E0E14)),
            border = BorderStroke(1.dp, Color(0xFF262636)),
            modifier = Modifier.width(540.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Dns, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Stream Sources & Mirrors",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                "${sources.size} mirror servers available",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "Close", tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Scrollable Sources list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sources.forEach { source ->
                        val isSelected = source.url == currentSource.url || source == currentSource
                        val qualityLabel = detectQualityLabel(source)
                        val cleanedTitle = cleanServerTitle(source.name)
                        val isDirectFile = source.url.lowercase().run {
                            endsWith(".mkv") || endsWith(".mp4") || endsWith(".m3u8") || contains("/movies/") || contains("gb") || contains("mb")
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(source) },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF221115) else Color(0xFF14141F)
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF1F1F2E)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Quality Badge
                                val (badgeBg, badgeBorder, badgeTextColor) = when (qualityLabel) {
                                    "4K" -> Triple(Color(0xFF2E2205), Color(0xFFFFB300), Color(0xFFFFC107))
                                    "1080p" -> Triple(Color(0xFF2C0A0D), MaterialTheme.colorScheme.primary, Color(0xFFFF5252))
                                    else -> Triple(Color(0xFF072133), Color(0xFF0288D1), Color(0xFF40C4FF))
                                }
                                Surface(
                                    color = badgeBg,
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, badgeBorder)
                                ) {
                                    Text(
                                        qualityLabel,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        color = badgeTextColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Server title & URL
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        cleanedTitle,
                                        color = Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        source.url,
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Direct File Badge
                                if (isDirectFile) {
                                    Surface(
                                        color = Color(0xFF0A2912),
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, Color(0xFF1B5E20))
                                    ) {
                                        Text(
                                            "⚡ Direct",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            color = Color(0xFF4CAF50),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Selection Check Icon
                                Icon(
                                    if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(0.3f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Audio Track Picker Dialog ────────────────────────────────────────────────

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
    tracks: List<TrackDescription>,
    selectedTrackId: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E0E14)),
            border = BorderStroke(1.dp, Color(0xFF262636)),
            modifier = Modifier.width(480.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Audiotrack, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Audio Track",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                "Select active audio stream language",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "Close", tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Scrollable Tracks List
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tracks.forEach { track ->
                        val isSelected = track.id() == selectedTrackId
                        val cleanName = cleanAudioTrackName(track.description())

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(track.id()) },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF221115) else Color(0xFF14141F)
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF1F1F2E)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(0.3f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        cleanName,
                                        color = Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                }
                                if (track.id() == -1) {
                                    Surface(
                                        color = Color.Gray.copy(0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            "Mute",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            color = Color.LightGray,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Combined Subtitle Picker Dialog ──────────────────────────────────────────

@Composable
private fun CombinedSubtitlePickerDialog(
    externalSubtitles: List<SubtitleTrackInfo>,
    selectedExternal: SubtitleTrackInfo?,
    nativeTracks: List<TrackDescription>,
    selectedNativeId: Int?,
    onSelectExternal: (SubtitleTrackInfo?) -> Unit,
    onSelectNative: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E0E14)),
            border = BorderStroke(1.dp, Color(0xFF262636)),
            modifier = Modifier.width(480.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ClosedCaption, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Subtitles",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                "Select subtitles track or embedded stream",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "Close", tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Scrollable Subtitles List
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Off Option
                    SubtitleOption("Off", selectedExternal == null && (selectedNativeId == null || selectedNativeId == -1), onClick = {
                        onSelectExternal(null)
                        onSelectNative(-1)
                    })

                    // Native Demuxed Tracks
                    if (nativeTracks.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Embedded Subtitles", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        nativeTracks.filter { it.id() != -1 }.forEach { track ->
                            val isSelected = track.id() == selectedNativeId
                            SubtitleOption(cleanAudioTrackName(track.description()), isSelected, onClick = { onSelectNative(track.id()) })
                        }
                    }

                    // External Fetched Subtitles
                    if (externalSubtitles.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("External Subtitles", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
    }
}

@Composable
private fun SubtitleOption(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF221115) else Color(0xFF14141F)
        ),
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF1F1F2E)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(0.3f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                label,
                color = Color.White,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp
            )
        }
    }
}

// ─── Speed Picker ─────────────────────────────────────────────────────────────

@Composable
private fun SpeedPickerDialog(current: Float, onSelect: (Float) -> Unit, onDismiss: () -> Unit) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 3.0f)
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E0E14)),
            border = BorderStroke(1.dp, Color(0xFF262636)),
            modifier = Modifier.width(360.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Playback Speed", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "Close", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    speeds.forEach { speed ->
                        val isSelected = speed == current
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(speed) },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF221115) else Color(0xFF14141F)
                            ),
                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF1F1F2E))
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    null, tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(0.3f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "${speed}x${if (speed == 1.0f) " (Normal)" else ""}",
                                    color = Color.White, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Volume Dialog ────────────────────────────────────────────────────────────

@Composable
private fun VolumeDialog(
    volume: Int, isMuted: Boolean,
    onVolumeChange: (Int) -> Unit,
    onMuteToggle: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E0E14)),
            border = BorderStroke(1.dp, Color(0xFF262636))
        ) {
            Column(modifier = Modifier.padding(20.dp).widthIn(min = 300.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Volume", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("${volume}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "Close", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMuteToggle) {
                        Icon(if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, null, tint = if (isMuted) MaterialTheme.colorScheme.error else Color.White)
                    }
                    Slider(
                        value = volume.toFloat(),
                        onValueChange = { onVolumeChange(it.toInt()) },
                        valueRange = 0f..200f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(0.15f)
                        )
                    )
                }
                if (volume > 100) {
                    Text("⚠ Volume above 100% may distort audio", color = Color(0xFFFFB300), fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                }
            }
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
    size: androidx.compose.ui.unit.Dp = 24.dp
) {
    IconButton(onClick = onClick) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(size))
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000) % 60; val m = (ms / 60000) % 60; val h = ms / 3600000
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
