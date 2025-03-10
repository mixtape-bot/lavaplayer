package com.sedmelluq.discord.lavaplayer.source.youtube

import com.sedmelluq.discord.lavaplayer.source.ItemSourceManager
import com.sedmelluq.discord.lavaplayer.source.common.LinkRouter
import com.sedmelluq.discord.lavaplayer.source.youtube.music.YoutubeMixLoader
import com.sedmelluq.discord.lavaplayer.source.youtube.music.YoutubeMixProvider
import com.sedmelluq.discord.lavaplayer.source.youtube.music.YoutubeSearchMusicProvider
import com.sedmelluq.discord.lavaplayer.source.youtube.music.YoutubeSearchMusicResultLoader
import com.sedmelluq.discord.lavaplayer.tools.extensions.closeWithWarnings
import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpConfigurable
import com.sedmelluq.discord.lavaplayer.tools.http.MultiHttpConfigurable
import com.sedmelluq.discord.lavaplayer.tools.io.*
import com.sedmelluq.discord.lavaplayer.track.AudioItem
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.loader.LoaderState
import com.sedmelluq.lava.common.tools.exception.FriendlyException
import com.sedmelluq.lava.common.tools.exception.friendlyError
import com.sedmelluq.lava.common.tools.exception.wrapUnfriendlyException
import com.sedmelluq.lava.track.info.AudioTrackInfo
import mu.KotlinLogging
import org.apache.http.client.methods.HttpGet
import java.io.DataInput

/**
 * Audio source manager that implements finding YouTube videos or playlists based on a URL or ID.
 *
 * @param allowSearch Whether to allow search queries as identifiers
 */
class YoutubeItemSourceManager @JvmOverloads constructor(
    private val allowSearch: Boolean = true,
    val trackDetailsLoader: YoutubeTrackDetailsLoader = DefaultYoutubeTrackDetailsLoader(),
    val searchResultLoader: YoutubeSearchResultLoader = YoutubeSearchProvider(),
    val searchMusicResultLoader: YoutubeSearchMusicResultLoader = YoutubeSearchMusicProvider(),
    val signatureResolver: YoutubeSignatureResolver = YoutubeSignatureCipherManager(),
    val playlistLoader: YoutubePlaylistLoader = DefaultYoutubePlaylistLoader(),
    val linkRouter: LinkRouter<YoutubeLinkRoutes> = DefaultYoutubeLinkRouter(),
    val mixLoader: YoutubeMixLoader = YoutubeMixProvider(),
) : ItemSourceManager, HttpConfigurable {
    companion object {
        private val log = KotlinLogging.logger { }
    }

    private val loadingRoutes: LoadingRoutes = LoadingRoutes()
    private val httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager()
    private val httpConfiguration = MultiHttpConfigurable(
        listOf(
            httpInterfaceManager,
            searchResultLoader.httpConfiguration,
            searchMusicResultLoader.httpConfiguration
        )
    )

    /**
     * @return Get an HTTP interface for a playing track.
     */
    val httpInterface: HttpInterface
        get() = httpInterfaceManager.get()

    val mainHttpConfiguration: ExtendedHttpConfigurable
        get() = httpInterfaceManager

    val searchHttpConfiguration: ExtendedHttpConfigurable
        get() = searchResultLoader.httpConfiguration

    val searchMusicHttpConfiguration: ExtendedHttpConfigurable
        get() = searchMusicResultLoader.httpConfiguration

    /**
     * Maximum number of pages loaded from one playlist. There are 100 tracks per page.
     */
    var playlistPageCount: Int
        get() = playlistLoader.playlistPageCount
        set(value) {
            playlistLoader.playlistPageCount = value
        }

    override val sourceName: String
        get() = "youtube"

    init {
        httpInterfaceManager.setHttpContextFilter(YoutubeHttpContextFilter())
    }

    override fun isTrackEncodable(track: AudioTrack): Boolean {
        return true
    }

    override fun shutdown() {
        httpInterfaceManager.closeWithWarnings()
    }

    override fun decodeTrack(trackInfo: AudioTrackInfo, input: DataInput, version: Int): AudioTrack {
        return YoutubeAudioTrack(trackInfo, this)
    }

    override fun configureRequests(configurator: RequestConfigurator) {
        return httpConfiguration.configureRequests(configurator)
    }

    override fun configureBuilder(configurator: BuilderConfigurator) {
        return httpConfiguration.configureBuilder(configurator)
    }

    override suspend fun loadItem(state: LoaderState, reference: AudioReference): AudioItem? {
        requireNotNull(reference.identifier) { "Reference identifier must not be null." }

        return try {
            loadItemOnce(reference.identifier!!)
        } catch (exception: FriendlyException) {
            // In case of a connection reset exception, try once more.
            if (HttpClientTools.isRetryableNetworkException(exception.cause ?: exception)) {
                loadItemOnce(reference.identifier!!)
            } else {
                throw exception
            }
        }
    }

    suspend fun loadItemOnce(identifier: String): AudioItem? {
        return linkRouter.find(identifier, loadingRoutes)
    }

    /**
     * Loads a single track from video ID.
     *
     * @param videoId   ID of the YouTube video.
     * @param mustExist True if it should throw an exception on missing track, otherwise returns AudioReference.NO_TRACK.
     * @return Loaded YouTube track.
     */
    fun loadTrackWithVideoId(videoId: String, mustExist: Boolean): AudioItem {
        try {
            httpInterface.use { httpInterface ->
                val details = trackDetailsLoader.loadDetails(httpInterface, videoId, false, this)
                    ?: if (mustExist) friendlyError("Video unavailable") else return AudioReference.NO_TRACK

                return YoutubeAudioTrack(details.trackInfo, this)
            }
        } catch (e: Exception) {
            throw e.wrapUnfriendlyException("Loading information for a YouTube track failed.",
                FriendlyException.Severity.FAULT)
        }
    }

    /**
     * Loads a track collection from a mix or playlist id
     *
     * @param type The type of playlist to load.
     * @param id The playlist id.
     * @param selectedVideoId The selected video id.
     */
    fun loadPlaylistWithId(type: PlaylistType, id: String, selectedVideoId: String?): AudioItem {
        log.debug { "Starting to load ${type.name.lowercase()} playlist $id, with selected track $selectedVideoId" }

        try {
            httpInterface.use { httpInterface ->
                val loader = if (type == PlaylistType.Mix) mixLoader else playlistLoader

                return loader.load(httpInterface, id, selectedVideoId) { buildTrackFromInfo(it) }
            }
        } catch (e: Exception) {
            if (selectedVideoId != null && e.message?.contains("does not exist", true) == true) {
                log.debug { "Playlist $id does not exist, attempting to return the selected video with ID $selectedVideoId" }
                return loadTrackWithVideoId(selectedVideoId, false)
            }

            log.debug { "Playlist $id does not exist." }
            throw e.wrapUnfriendlyException()
        }
    }

    private fun buildTrackFromInfo(info: AudioTrackInfo): YoutubeAudioTrack {
        return YoutubeAudioTrack(info, this)
    }

    private inner class LoadingRoutes : YoutubeLinkRoutes {
        override fun track(videoId: String): AudioItem {
            return loadTrackWithVideoId(videoId, false)
        }

        override fun playlist(playlistId: String, selectedVideoId: String?): AudioItem {
            return loadPlaylistWithId(PlaylistType.Regular, playlistId, selectedVideoId)
        }

        override fun mix(mixId: String, selectedVideoId: String?): AudioItem {
            return loadPlaylistWithId(PlaylistType.Mix, mixId, selectedVideoId)
        }

        override fun search(query: String): AudioItem? {
            if (!allowSearch) {
                return null
            }

            return searchResultLoader.loadSearchResult(query) { buildTrackFromInfo(it) }
        }

        override fun searchMusic(query: String): AudioItem? {
            if (!allowSearch) {
                return null
            }

            return searchMusicResultLoader.loadSearchMusicResult(query) { buildTrackFromInfo(it) }
        }

        override fun anonymous(videoIds: String): AudioItem {
            try {
                httpInterface.use { httpInterface ->
                    httpInterface.execute(HttpGet("https://www.youtube.com/watch_videos?video_ids=$videoIds"))
                        .use { response ->
                            HttpClientTools.assertSuccessWithContent(response, "playlist response")

                            /* YouTube currently transforms watch_video links into a link with a video id and a list id.
                               because that's what happens, we can simply re-process with the redirected link */
                            val redirects = httpInterface.context.redirectLocations?.takeUnless { it.isEmpty() }
                                ?: friendlyError(
                                    "Unable to process YouTube watch_videos link",
                                    FriendlyException.Severity.SUSPICIOUS,
                                    IllegalStateException("Expected YouTube to redirect watch_videos link to a watch?v={id}&list={list_id} link, but it did not redirect at all")
                                )

                            return AudioReference(redirects[0].toString(), null)
                        }
                }
            } catch (e: Exception) {
                throw e.wrapUnfriendlyException()
            }
        }

        override fun none(): AudioItem =
            AudioReference.NO_TRACK
    }

    enum class PlaylistType { Mix, Regular }
}
