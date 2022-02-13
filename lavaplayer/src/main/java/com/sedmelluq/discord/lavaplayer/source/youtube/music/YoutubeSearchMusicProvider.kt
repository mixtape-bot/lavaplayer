package com.sedmelluq.discord.lavaplayer.source.youtube.music

import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeConstants
import com.sedmelluq.discord.lavaplayer.tools.ThumbnailTools
import com.sedmelluq.discord.lavaplayer.tools.extensions.parseMilliseconds
import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpConfigurable
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import com.sedmelluq.discord.lavaplayer.tools.json.JsonBrowser
import com.sedmelluq.discord.lavaplayer.track.AudioItem
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackFactory
import com.sedmelluq.discord.lavaplayer.track.collection.SearchResult
import com.sedmelluq.lava.common.tools.exception.wrapUnfriendlyException
import com.sedmelluq.lava.track.info.BasicAudioTrackInfo
import mu.KotlinLogging
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import java.io.IOException

/**
 * Handles processing YouTube Music searches.
 */
class YoutubeSearchMusicProvider : YoutubeSearchMusicResultLoader {
    companion object {
        private val log = KotlinLogging.logger {  }
    }

    private val httpInterfaceManager = HttpClientTools.createCookielessThreadLocalManager()

    override val httpConfiguration: ExtendedHttpConfigurable
        get() = httpInterfaceManager

    /**
     * @param query        Search query.
     * @param trackFactory
     * @return Playlist of the first page of music results.
     */
    override fun loadSearchMusicResult(query: String, trackFactory: AudioTrackFactory): AudioItem {
        log.debug { "Performing a search music with query $query" }
        try {
            httpInterfaceManager.get().use { httpInterface ->
                val post = HttpPost(YoutubeConstants.MUSIC_SEARCH_URL)

                post.setHeader("Referer", "music.youtube.com")
                post.entity = StringEntity(YoutubeConstants.MUSIC_SEARCH_PAYLOAD.format(query.replace("\"", "\\\"")), "UTF-8")

                httpInterface.execute(post).use { response ->
                    HttpClientTools.assertSuccessWithContent(response, "search music response")
                    val jsonBrowser = JsonBrowser.parse(response.entity.content)
                    return extractSearchResults(jsonBrowser, query, trackFactory)
                }
            }
        } catch (e: Exception) {
            throw e.wrapUnfriendlyException()
        }
    }

    private fun extractSearchResults(
        jsonBrowser: JsonBrowser,
        query: String,
        trackFactory: AudioTrackFactory
    ): AudioItem {
        log.debug { "Attempting to parse results from music search page" }

        val tracks: List<AudioTrack> = try {
            extractMusicSearchPage(jsonBrowser, trackFactory)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK
        }

        return SearchResult(query, tracks.toMutableList())
    }

    @Throws(IOException::class)
    private fun extractMusicSearchPage(jsonBrowser: JsonBrowser, trackFactory: AudioTrackFactory): List<AudioTrack> {
        var tracks =
            jsonBrowser["contents"]["tabbedSearchResultsRenderer"]["tabs"][0]["tabRenderer"]["content"]["sectionListRenderer"]["contents"][0]["musicShelfRenderer"]["contents"]

        if (tracks.isNull) {
            tracks =
                jsonBrowser["contents"]["tabbedSearchResultsRenderer"]["tabs"][0]["tabRenderer"]["content"]["sectionListRenderer"]["contents"][1]["musicShelfRenderer"]["contents"]
        }

        return tracks.values()
            .mapNotNull { extractMusicTrack(it, trackFactory) }
            .toMutableList()
    }

    private fun extractMusicTrack(jsonBrowser: JsonBrowser, trackFactory: AudioTrackFactory): AudioTrack? {
        val columns = jsonBrowser["musicResponsiveListItemRenderer"]["flexColumns"].takeUnless { it.isNull }
            ?: return null // Somehow didn't get track info, ignore

        val firstColumn = columns[0]["musicResponsiveListItemFlexColumnRenderer"]["text"]["runs"][0]
        val title = firstColumn["text"].text

        /* If track is not available on YouTube Music videoId will be empty */
        val videoId = firstColumn["navigationEndpoint"]["watchEndpoint"]["videoId"].text
            ?: return null

        val secondColumn = columns[1]["musicResponsiveListItemFlexColumnRenderer"]["text"]["runs"].values()
        val author = secondColumn[0]["text"].safeText

        /* The duration element should not have this key, if it does, then duration is probably missing, so return */
        val lastElement = secondColumn[secondColumn.size - 1]
        if (!lastElement["navigationEndpoint"].isNull) {
            return null
        }

        return trackFactory.create(BasicAudioTrackInfo(
            title = title!!,
            author = author,
            length = lastElement["text"].text!!.parseMilliseconds(),
            identifier = videoId,
            uri = YoutubeConstants.WATCH_URL_PREFIX + videoId,
            artworkUrl = ThumbnailTools.extractYouTubeMusic(jsonBrowser, videoId),
            isStream = false
        ))
    }
}
