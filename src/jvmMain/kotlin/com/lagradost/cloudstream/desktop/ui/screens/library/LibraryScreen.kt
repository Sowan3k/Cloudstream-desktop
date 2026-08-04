package com.lagradost.cloudstream.desktop.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream.desktop.storage.DesktopDataStore
import com.lagradost.cloudstream.desktop.storage.WatchProgress
import com.lagradost.cloudstream.desktop.ui.navigation.DesktopScreen

@Composable
fun LibraryScreen(
    onNavigate: (DesktopScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val watchHistory = DesktopDataStore.currentSettings.watchHistory
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Continue Watching", "History")

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
        // Header
        Text(
            "My Library",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = Color.White
        )
        Text(
            "${watchHistory.size} title${if (watchHistory.size != 1) "s" else ""} in history",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(20.dp))

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF1A1A22),
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            title,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == index) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        if (watchHistory.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.VideoLibrary,
                        null,
                        tint = Color.Gray.copy(alpha = 0.4f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Nothing here yet", color = Color.Gray, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Watch something to see it here",
                        color = Color.Gray.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { onNavigate(DesktopScreen.Home) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Home, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Browse Content")
                    }
                }
            }
        } else {
            when (selectedTab) {
                0 -> ContinueWatchingGrid(
                    items = watchHistory.filter { it.positionMillis > 0 && it.durationMillis > 0 },
                    onNavigate = onNavigate
                )
                1 -> HistoryGrid(items = watchHistory, onNavigate = onNavigate)
            }
        }
    }
}

@Composable
private fun ContinueWatchingGrid(
    items: List<WatchProgress>,
    onNavigate: (DesktopScreen) -> Unit
) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PlayCircleOutline, null, tint = Color.Gray.copy(alpha = 0.4f), modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text("No in-progress titles", color = Color.Gray)
                Text("Start watching something!", color = Color.Gray.copy(alpha = 0.6f), fontSize = 13.sp)
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 240.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items) { progress ->
                WatchProgressCard(
                    progress = progress,
                    onClick = {
                        onNavigate(DesktopScreen.Result(url = progress.url, apiName = ""))
                    }
                )
            }
        }
    }
}

@Composable
private fun HistoryGrid(
    items: List<WatchProgress>,
    onNavigate: (DesktopScreen) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 240.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items.reversed()) { progress ->
            WatchProgressCard(
                progress = progress,
                onClick = {
                    onNavigate(DesktopScreen.Result(url = progress.url, apiName = ""))
                }
            )
        }
    }
}

@Composable
private fun WatchProgressCard(progress: WatchProgress, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(110.dp).clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2A))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(Color(0xFF1A2A3A), Color(0xFF0D1520)))
                )
            )
            Column(
                modifier = Modifier.fillMaxSize().padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.PlayCircle,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            progress.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 14.sp
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                }

                Column {
                    if (progress.durationMillis > 0) {
                        val pct = (progress.positionMillis.toFloat() / progress.durationMillis).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { pct },
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.15f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${(pct * 100).toInt()}% watched",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                            Text(
                                formatTime(progress.positionMillis) + " / " + formatTime(progress.durationMillis),
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000) % 60; val m = (ms / 60000) % 60; val h = ms / 3600000
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
