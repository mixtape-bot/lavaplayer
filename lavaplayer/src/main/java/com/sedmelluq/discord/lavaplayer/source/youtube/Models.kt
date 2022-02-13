package com.sedmelluq.discord.lavaplayer.source.youtube

import com.sedmelluq.discord.lavaplayer.tools.ThumbnailTools
import com.sedmelluq.discord.lavaplayer.tools.extensions.parseMilliseconds
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class YouTubeVideoModel @OptIn(ExperimentalSerializationApi::class) constructor(
    @SerialName("videoId")
    val id: String,
    val isPlayable: Boolean? = null,
    val lengthSeconds: String? = null,
    @SerialName("title")
    private val titleRuns: TitleRuns,
    @JsonNames("longBylineText", "shortBylineText")
    private val authorRuns: YouTubeRunsModel? = null,
    @SerialName("lengthText")
    private val lengthRuns: YouTubeRunsModel? = null,
    @SerialName("thumbnail")
    private val _thumbnail: Thumbnails
) {
    val title: String
        get() = titleRuns.simpleText
            ?: titleRuns.runs.first().text

    val author: String?
        get() = authorRuns?.runs?.firstOrNull()?.text

    val length: Long?
        get() = lengthSeconds?.let { it.toLong() * 1000 }
            ?: lengthRuns?.runs?.firstOrNull()?.text?.parseMilliseconds()

    val thumbnail: String
        get() = _thumbnail.thumbnails.maxByOrNull { it.width + it.height }?.url
            ?: ThumbnailTools.YOUTUBE_THUMBNAIL_FORMAT.format(id)

    @Serializable
    data class Thumbnails(val thumbnails: List<TrackThumbnail>)

    @Serializable
    data class TrackThumbnail(val url: String, val width: Long, val height: Long)

    @Serializable
    data class TitleRuns(val simpleText: String? = null, val runs: List<YouTubeRunsModel.Run>)
}

@Serializable
data class YouTubeRunsModel(val runs: List<Run>) {
    @Serializable
    data class Run(val text: String)
}

