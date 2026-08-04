package com.lagradost.cloudstream.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.lagradost.cloudstream.desktop.plugins.DesktopPluginLoader
import com.lagradost.cloudstream.desktop.storage.DesktopDataStore
import com.lagradost.cloudstream.desktop.ui.navigation.DesktopScreen
import com.lagradost.cloudstream.desktop.ui.navigation.DesktopSidebar
import com.lagradost.cloudstream.desktop.ui.screens.home.HomeScreen
import com.lagradost.cloudstream.desktop.ui.screens.library.LibraryScreen
import com.lagradost.cloudstream.desktop.ui.screens.player.PlayerScreen
import com.lagradost.cloudstream.desktop.ui.screens.plugins.PluginsScreen
import com.lagradost.cloudstream.desktop.ui.screens.result.ResultScreen
import com.lagradost.cloudstream.desktop.ui.screens.search.SearchScreen
import com.lagradost.cloudstream.desktop.ui.screens.settings.SettingsScreen
import com.lagradost.cloudstream.desktop.ui.theme.CloudStreamDesktopTheme

fun main() {
    // Setup Coil 3 image loader with OkHttp, User-Agent, and TrustAll SSL for poster URLs
    com.lagradost.cloudstream.desktop.utils.setupDesktopImageLoader()

    // Register native BDIX extensions and load cached plugins
    DesktopPluginLoader.registerNehalProviders()
    DesktopPluginLoader.loadAllLocalPlugins()

    application {
        // Load local desktop settings
        val settings = remember { DesktopDataStore.currentSettings }
        val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)

        val toggleFullscreen = {
            windowState.placement = if (windowState.placement == WindowPlacement.Fullscreen) {
                WindowPlacement.Floating
            } else {
                WindowPlacement.Fullscreen
            }
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = "CloudStream Windows Desktop",
            icon = painterResource("icon.png"),
            state = windowState,
            undecorated = true
        ) {
            val isFullscreen = windowState.placement == WindowPlacement.Fullscreen

            // Register CloudStream library providers and cached JAR plugins on window composition
            LaunchedEffect(Unit) {
                DesktopPluginLoader.loadAllLocalPlugins()
            }

            CloudStreamDesktopTheme(darkTheme = settings.isDarkMode) {
                var currentScreen by remember { mutableStateOf<DesktopScreen>(DesktopScreen.Home) }
                val screenHistory = remember { mutableStateListOf<DesktopScreen>() }

                fun navigateTo(screen: DesktopScreen) {
                    screenHistory.add(currentScreen)
                    currentScreen = screen
                }

                fun navigateBack() {
                    if (screenHistory.isNotEmpty()) {
                        currentScreen = screenHistory.removeLast()
                    } else {
                        currentScreen = DesktopScreen.Home
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Custom dark title bar for windowed mode (hidden in Fullscreen)
                    if (!isFullscreen) {
                        WindowDraggableArea {
                            CustomTitleBar(
                                title = "CloudStream Windows Desktop",
                                windowState = windowState,
                                onClose = ::exitApplication
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            // Sidebar visible for non-player screens
                            if (currentScreen !is DesktopScreen.Player) {
                                DesktopSidebar(
                                    currentScreen = currentScreen,
                                    onNavigate = { navigateTo(it) }
                                )
                            }

                            // Main screen view canvas
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                when (val screen = currentScreen) {
                                    is DesktopScreen.Home -> HomeScreen(
                                        onNavigate = { navigateTo(it) }
                                    )

                                    is DesktopScreen.Search -> SearchScreen(
                                        initialQuery = screen.query,
                                        onNavigate = { navigateTo(it) }
                                    )

                                    is DesktopScreen.Result -> ResultScreen(
                                        url = screen.url,
                                        apiName = screen.apiName,
                                        searchResponse = screen.searchResponse,
                                        onNavigate = { navigateTo(it) },
                                        onBack = { navigateBack() }
                                    )

                                    is DesktopScreen.Player -> PlayerScreen(
                                        mediaUrl = screen.mediaUrl,
                                        title = screen.title,
                                        episodeName = screen.episodeName,
                                        posterUrl = screen.posterUrl,
                                        headers = screen.headers,
                                        subtitles = screen.subtitles,
                                        sources = screen.sources,
                                        loadResponse = screen.loadResponse,
                                        currentEpisode = screen.currentEpisode,
                                        apiName = screen.apiName,
                                        isFullscreen = isFullscreen,
                                        onToggleFullscreen = toggleFullscreen,
                                        onBack = { navigateBack() }
                                    )

                                    is DesktopScreen.Plugins -> PluginsScreen()

                                    is DesktopScreen.Library -> LibraryScreen(
                                        onNavigate = { navigateTo(it) }
                                    )

                                    is DesktopScreen.Settings -> SettingsScreen()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WindowScope.CustomTitleBar(
    title: String,
    windowState: WindowState,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(Color(0xFF0D0D12))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource("icon.png"),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(title, color = Color.LightGray, fontSize = 12.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Minimize
            IconButton(
                onClick = { windowState.isMinimized = true },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(Icons.Default.Minimize, "Minimize", tint = Color.LightGray, modifier = Modifier.size(14.dp))
            }
            // Maximize / Restore
            IconButton(
                onClick = {
                    windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                        WindowPlacement.Floating
                    } else {
                        WindowPlacement.Maximized
                    }
                },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    if (windowState.placement == WindowPlacement.Maximized) Icons.Default.CropSquare else Icons.Default.CheckBoxOutlineBlank,
                    "Maximize", tint = Color.LightGray, modifier = Modifier.size(14.dp)
                )
            }
            // Close
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(Icons.Default.Close, "Close", tint = Color(0xFFFF5555), modifier = Modifier.size(14.dp))
            }
        }
    }
}
