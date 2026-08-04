package com.lagradost.cloudstream.desktop.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class WatchProgress(
    val title: String,
    val url: String,
    val posterUrl: String? = null,
    val positionMillis: Long = 0,
    val durationMillis: Long = 0,
    val episodeIndex: Int? = null,
    val seasonIndex: Int? = null,
    val apiName: String = "",
    val lastWatchedTimestamp: Long = System.currentTimeMillis()
)

@Serializable
data class WatchlistItem(
    val title: String,
    val url: String,
    val posterUrl: String? = null,
    val apiName: String = "",
    val tvType: String = "",
    val addedTimestamp: Long = System.currentTimeMillis()
)

@Serializable
data class AppSettings(
    val selectedProvider: String = "All",
    val pluginRepositories: List<String> = listOf("https://raw.githubusercontent.com/recloudstream/cloudstream-extensions/master/repo.json"),
    val subtitleLanguage: String = "English",
    val defaultQuality: String = "1080p",
    val watchHistory: List<WatchProgress> = emptyList(),
    val watchlist: List<WatchlistItem> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val isDarkMode: Boolean = true
)

object DesktopDataStore {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val storageDir: File by lazy {
        val userHome = System.getProperty("user.home") ?: "."
        val dir = File(userHome, ".cloudstream_desktop")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    private val settingsFile: File by lazy {
        File(storageDir, "settings.json")
    }

    var currentSettings: AppSettings = loadSettings()
        private set

    fun loadSettings(): AppSettings {
        return try {
            if (settingsFile.exists()) {
                json.decodeFromString(settingsFile.readText())
            } else {
                AppSettings()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            AppSettings()
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        currentSettings = transform(currentSettings)
        try {
            settingsFile.writeText(json.encodeToString(currentSettings))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveWatchProgress(progress: WatchProgress) {
        updateSettings { settings ->
            val updatedHistory = settings.watchHistory.filterNot { it.url == progress.url }.toMutableList()
            updatedHistory.add(0, progress)
            settings.copy(watchHistory = updatedHistory)
        }
    }

    fun removeFromHistory(url: String) {
        updateSettings { settings ->
            settings.copy(watchHistory = settings.watchHistory.filterNot { it.url == url })
        }
    }

    fun clearHistory() {
        updateSettings { settings -> settings.copy(watchHistory = emptyList()) }
    }

    fun addToWatchlist(item: WatchlistItem) {
        updateSettings { settings ->
            val updated = settings.watchlist.filterNot { it.url == item.url }.toMutableList()
            updated.add(0, item)
            settings.copy(watchlist = updated)
        }
    }

    fun removeFromWatchlist(url: String) {
        updateSettings { settings ->
            settings.copy(watchlist = settings.watchlist.filterNot { it.url == url })
        }
    }

    fun isInWatchlist(url: String): Boolean =
        currentSettings.watchlist.any { it.url == url }

    fun saveSearchQuery(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        updateSettings { settings ->
            val updated = settings.searchHistory.filterNot { it.equals(q, ignoreCase = true) }.toMutableList()
            updated.add(0, q)
            val trimmed = if (updated.size > 20) updated.take(20) else updated
            settings.copy(searchHistory = trimmed)
        }
    }

    fun removeSearchQuery(query: String) {
        updateSettings { settings ->
            settings.copy(searchHistory = settings.searchHistory.filterNot { it.equals(query, ignoreCase = true) })
        }
    }

    fun clearSearchHistory() {
        updateSettings { settings ->
            settings.copy(searchHistory = emptyList())
        }
    }
}
