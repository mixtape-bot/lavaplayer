package com.sedmelluq.discord.lavaplayer.source.twitch

import com.sedmelluq.discord.lavaplayer.source.ItemSourceManager
import com.sedmelluq.discord.lavaplayer.tools.Units
import com.sedmelluq.discord.lavaplayer.tools.extensions.closeWithWarnings
import com.sedmelluq.discord.lavaplayer.tools.io.*
import com.sedmelluq.discord.lavaplayer.tools.json.JsonBrowser
import com.sedmelluq.discord.lavaplayer.track.AudioItem
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.loader.LoaderState
import com.sedmelluq.lava.common.tools.exception.FriendlyException
import com.sedmelluq.lava.common.tools.exception.friendlyError
import com.sedmelluq.lava.track.info.AudioTrackInfo
import com.sedmelluq.lava.track.info.BasicAudioTrackInfo
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.entity.StringEntity
import java.io.DataInput
import java.io.IOException
import java.util.regex.Pattern


/**
 * Audio source manager which detects Twitch tracks by URL.
 *
 * @param clientId The Twitch client id for your application.
 */
class TwitchStreamItemSourceManager @JvmOverloads constructor(
    private val clientId: String = TwitchConstants.DEFAULT_CLIENT_ID,
) : ItemSourceManager, HttpConfigurable {
    companion object {
        private const val STREAM_NAME_REGEX = "^https://(?:www\\.|go\\.)?twitch.tv/([^/]+)$"

        private val streamNameRegex = Pattern.compile(STREAM_NAME_REGEX)

        /**
         * Extract channel identifier from a channel URL.
         *
         * @param url Channel URL
         * @return Channel identifier (for API requests)
         */
        fun getChannelIdentifierFromUrl(url: String): String? {
            val matcher = streamNameRegex.matcher(url)
            return if (!matcher.matches()) null else matcher.group(1)
        }

        private fun <T : HttpUriRequest> addClientHeaders(request: T, clientId: String): T {
            request.setHeader("Client-ID", clientId)
            return request
        }
    }

    private val httpInterfaceManager: HttpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager()

    /**
     * @return Get an HTTP interface for a playing track.
     */
    val httpInterface: HttpInterface
        get() = httpInterfaceManager.get()

    override val sourceName: String
        get() = "twitch"

    /**
     * @param url Request URL
     * @return Request with necessary headers attached.
     */
    fun createGetRequest(url: String): HttpGet =
        addClientHeaders(HttpGet(url), clientId)

    /**
     * @param url Request URL
     * @return Request with necessary headers attached.
     */
    fun createPostRequest(url: String): HttpPost =
        addClientHeaders(HttpPost(url), clientId)

    override suspend fun loadItem(state: LoaderState, reference: AudioReference): AudioItem? {
        val streamName = getChannelIdentifierFromUrl(reference.identifier!!)
            ?: return null

        fetchAccessToken(streamName)?.takeUnless { it["data"]["streamPlaybackAccessToken"].isNull }
            ?: return AudioReference.NO_TRACK

        val channelInfo = fetchStreamChannelInfo(streamName)
            ?.get("data")
            ?.get("user")
            ?.takeUnless { it["stream"]["type"].isNull }
            ?: return AudioReference.NO_TRACK

        val info = BasicAudioTrackInfo(
            title = channelInfo["lastBroadcast"]["title"].safeText,
            author = streamName,
            length = Units.DURATION_MS_UNKNOWN,
            identifier = reference.identifier!!,
            uri = reference.identifier,
            artworkUrl = channelInfo["profileImageURL"].safeText.replaceFirst("-70x70", "-300x300"),
            isStream = true
        )

        return TwitchStreamAudioTrack(info, this)
    }

    override fun isTrackEncodable(track: AudioTrack): Boolean {
        return true
    }

    override fun shutdown() {
        return httpInterfaceManager.closeWithWarnings()
    }

    override fun decodeTrack(trackInfo: AudioTrackInfo, input: DataInput, version: Int): AudioTrack {
        return TwitchStreamAudioTrack(trackInfo, this)
    }

    override fun configureRequests(configurator: RequestConfigurator) {
        httpInterfaceManager.configureRequests(configurator)
    }

    override fun configureBuilder(configurator: BuilderConfigurator) {
        httpInterfaceManager.configureBuilder(configurator)
    }

    private fun fetchAccessToken(name: String): JsonBrowser? {
        try {
            httpInterface.use { httpInterface ->
                val post = createPostRequest(TwitchConstants.TWITCH_GRAPHQL_BASE_URL)
                post.entity = StringEntity(TwitchConstants.ACCESS_TOKEN_PAYLOAD.format(name))

                return HttpClientTools.fetchResponseAsJson(httpInterface, post)
            }
        } catch (e: IOException) {
            friendlyError("Loading Twitch channel access token failed.", FriendlyException.Severity.SUSPICIOUS, e)
        }
    }

    private fun fetchStreamChannelInfo(channelId: String): JsonBrowser? {
        try {
            httpInterface.use { httpInterface ->
                val post = createPostRequest(TwitchConstants.TWITCH_GRAPHQL_BASE_URL)
                post.entity = StringEntity(TwitchConstants.METADATA_PAYLOAD.format(channelId))

                return HttpClientTools.fetchResponseAsJson(httpInterface, post)
            }
        } catch (e: IOException) {
            friendlyError("Loading Twitch channel information failed.", FriendlyException.Severity.SUSPICIOUS, e)
        }
    }
}
