package com.lagradost.cloudstream.desktop.ui.navigation

import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SearchResponse

import com.lagradost.cloudstream3.Episode

sealed class DesktopScreen {
    data object Home : DesktopScreen()
    data class Search(val query: String = "") : DesktopScreen()
    data class Result(val url: String, val apiName: String, val searchResponse: SearchResponse? = null) : DesktopScreen()
    data class Player(
        val mediaUrl: String,
        val title: String,
        val episodeName: String? = null,
        val posterUrl: String? = null,
        val headers: Map<String, String> = emptyMap(),
        val subtitles: List<SubtitleTrackInfo> = emptyList(),
        val sources: List<StreamSourceInfo> = emptyList(),
        val loadResponse: LoadResponse? = null,
        val currentEpisode: Episode? = null,
        val apiName: String = ""
    ) : DesktopScreen()
    data object Library : DesktopScreen()
    data object Plugins : DesktopScreen()
    data object Settings : DesktopScreen()
}

data class SubtitleTrackInfo(
    val url: String,
    val language: String
)

data class StreamSourceInfo(
    val name: String,
    val url: String,
    val quality: Int,
    val headers: Map<String, String> = emptyMap()
)
