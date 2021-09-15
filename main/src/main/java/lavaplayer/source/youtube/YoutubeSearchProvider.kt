package lavaplayer.source.youtube

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import lavaplayer.tools.DataFormatTools.durationTextToMillis
import lavaplayer.tools.ExceptionTools
import lavaplayer.tools.ThumbnailTools
import lavaplayer.tools.extensions.decodeJson
import lavaplayer.tools.http.ExtendedHttpConfigurable
import lavaplayer.tools.io.HttpClientTools
import lavaplayer.tools.io.HttpInterfaceManager
import lavaplayer.track.*
import mu.KotlinLogging
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.util.EntityUtils
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Handles processing YouTube searches.
 */
class YoutubeSearchProvider : YoutubeSearchResultLoader {
    companion object {
        private val log = KotlinLogging.logger { }
    }

    private val httpInterfaceManager: HttpInterfaceManager = HttpClientTools.createCookielessThreadLocalManager()

    override val httpConfiguration: ExtendedHttpConfigurable
        get() = httpInterfaceManager

    /**
     * @param query Search query.
     * @return Playlist of the first page of results.
     */
    override fun loadSearchResult(query: String, trackFactory: AudioTrackFactory): AudioItem {
        log.debug { "Performing a search with query $query" }
        try {
            httpInterfaceManager.get().use { httpInterface ->
                val post = HttpPost(YoutubeConstants.SEARCH_URL)
                post.entity = StringEntity(YoutubeConstants.SEARCH_PAYLOAD.format(query.replace("\"", "\\\"")), "UTF-8")

                httpInterface.execute(post).use { response ->
                    HttpClientTools.assertSuccessWithContent(response, "search response")
                    val responseText = EntityUtils.toString(response.entity, StandardCharsets.UTF_8)
                    val searchResults = responseText.decodeJson<YouTubeSearchResult>()
                    return extractSearchResults(searchResults, query, trackFactory)
                }
            }
        } catch (e: Exception) {
            throw ExceptionTools.wrapUnfriendlyException(e)
        }
    }

    private fun extractSearchResults(
        searchResults: YouTubeSearchResult,
        query: String,
        trackFactory: AudioTrackFactory
    ): AudioItem {
        log.debug { "Attempting to parse results from search page" }

        val tracks: MutableList<AudioTrack> = searchResults
            .runCatching { extractSearchPage(this, trackFactory) }
            .onFailure { throw RuntimeException(it) }
            .getOrThrow()

        return if (tracks.isEmpty()) {
            AudioReference.NO_TRACK
        } else {
            BasicAudioTrackCollection(
                "Search results for: $query",
                AudioTrackCollectionType.SearchResult(query),
                tracks,
                null
            )
        }
    }

    @Throws(IOException::class)
    private fun extractSearchPage(
        searchResults: YouTubeSearchResult,
        trackFactory: AudioTrackFactory
    ): MutableList<AudioTrack> {
        return searchResults.contents.sectionListRenderer.contents.first().itemSectionRenderer.videos
            .mapNotNull { extractPolymerData(it, trackFactory) }
            .toMutableList()
    }

    private fun extractPolymerData(listedTrack: SectionListTrack, trackFactory: AudioTrackFactory): AudioTrack? {
        val track = listedTrack.data
            ?: return null // Ignore everything which is not a track

        /* Ignore if the video is a live stream */
        val length = track.length
            ?: return null

        /* create the audio track. */
        val info = AudioTrackInfo(
            title = track.title,
            author = track.author,
            length = length,
            identifier = track.id,
            uri = "${YoutubeConstants.WATCH_URL_PREFIX}${track.id}",
            artworkUrl = track.thumbnail
        )

        return trackFactory.create(info)
    }

    @Serializable
    data class YouTubeSearchResult(val contents: YouTubeSearchResultContents)

    @Serializable
    data class YouTubeSearchResultContents(val sectionListRenderer: SectionList)

    @Serializable
    data class SectionList(val contents: List<SectionListItem>)

    @Serializable
    data class SectionListItem(val itemSectionRenderer: SectionListItemRenderer)

    @Serializable
    data class SectionListItemRenderer(@SerialName("contents") val videos: List<SectionListTrack>)

    @Serializable
    data class SectionListTrack(@SerialName("compactVideoRenderer") val data: Video? = null)

    @Serializable
    data class Video(
        @SerialName("videoId")
        val id: String,
        @SerialName("title")
        private val titleRuns: Runs,
        @SerialName("longBylineText")
        private val authorRuns: Runs,
        @SerialName("lengthText")
        private val lengthRuns: Runs?,
        @SerialName("thumbnail")
        private val _thumbnail: Thumbnails
    ) {
        val title: String
            get() = titleRuns.runs.first().text

        val author: String
            get() = authorRuns.runs.first().text

        val length: Long?
            get() = lengthRuns?.runs?.firstOrNull()?.text?.let { durationTextToMillis(it) }

        val thumbnail: String
            get() = _thumbnail.thumbnails.maxByOrNull { it.width + it.height }?.url
                ?: ThumbnailTools.YOUTUBE_THUMBNAIL_FORMAT.format(id)

        @Serializable
        data class Thumbnails(val thumbnails: List<TrackThumbnail>)
    }

    @Serializable
    data class TrackThumbnail(val url: String, val width: Long, val height: Long)

    @Serializable
    data class Runs(val runs: List<Run>) {
        @Serializable
        data class Run(val text: String)
    }
}
