package com.sedmelluq.discord.lavaplayer.source.twitch

import com.sedmelluq.discord.lavaplayer.source.ItemSourceManager
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.tools.Units
import com.sedmelluq.discord.lavaplayer.tools.extensions.decodeJson
import com.sedmelluq.discord.lavaplayer.tools.io.*
import com.sedmelluq.discord.lavaplayer.tools.json.JsonBrowser
import com.sedmelluq.discord.lavaplayer.track.AudioItem
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import com.sedmelluq.discord.lavaplayer.track.loader.LoaderState
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpUriRequest
import java.io.DataInput
import java.io.IOException
import java.net.URI
import java.util.regex.Pattern

/**
 * Audio source manager which detects Twitch tracks by URL.
 *
 * @param clientId The Twitch client id for your application.
 */
class TwitchStreamItemSourceManager @JvmOverloads constructor(
    private val clientId: String = DEFAULT_CLIENT_ID
) : ItemSourceManager, HttpConfigurable {
    companion object {
        const val DEFAULT_CLIENT_ID = "jzkbprff40iqj646a697cyrvl0zt2m6"
        private const val STREAM_NAME_REGEX = "^https://(?:www\\.|go\\.)?twitch.tv/([^/]+)$"
        private val streamNameRegex = Pattern.compile(STREAM_NAME_REGEX)

        /**
         * Extract channel identifier from a channel URL.
         *
         * @param url Channel URL
         * @return Channel identifier (for API requests)
         */
        fun getChannelIdentifierFromUrl(url: String?): String? {
            val matcher = streamNameRegex.matcher(url)
            return if (!matcher.matches()) {
                null
            } else {
                matcher.group(1)
            }
        }

        private fun addClientHeaders(request: HttpUriRequest, clientId: String): HttpUriRequest {
            request.setHeader("Accept", "application/vnd.twitchtv.v5+json; charset=UTF-8")
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
    fun createGetRequest(url: String?): HttpUriRequest {
        return addClientHeaders(HttpGet(url), clientId)
    }

    /**
     * @param url Request URL
     * @return Request with necessary headers attached.
     */
    fun createGetRequest(url: URI?): HttpUriRequest {
        return addClientHeaders(HttpGet(url), clientId)
    }

    override suspend fun loadItem(state: LoaderState, reference: AudioReference): AudioItem? {
        val streamName = getChannelIdentifierFromUrl(reference.identifier)
            ?: return null

        val accessToken = fetchAccessToken(streamName)
        if (accessToken == null || accessToken["token"].isNull) {
            return AudioReference.NO_TRACK
        }

        val channelId: String
        try {
            val token: JsonBrowser = accessToken["token"].text!!.decodeJson()
            channelId = token["channel_id"].safeText
        } catch (e: IOException) {
            return null
        }

        val channelInfo = fetchStreamChannelInfo(channelId)
        return if (channelInfo == null || channelInfo["stream"].isNull) {
            AudioReference.NO_TRACK
        } else {
            /*
              --- HELIX STUFF
              //Retrieve the data value list; this will have only one element since we're getting only one stream's information
              List<JsonBrowser> dataList = channelInfo.get("data").values();

              //The value list is empty if the stream is offline, even when hosting another channel
              if (dataList.size() == 0){
                  return null;
              }

              //The first one has the title of the broadcast
              JsonBrowser channelData = dataList.get(0);
              String status = channelData.get("title").text();
               */
            val channelData = channelInfo["stream"]["channel"]
            val status = channelData["status"].text
            val thumbnail = channelData["logo"].text
            TwitchStreamAudioTrack(
                AudioTrackInfo(
                    status!!,
                    streamName,
                    Units.DURATION_MS_UNKNOWN,
                    reference.identifier!!,
                    reference.identifier,
                    thumbnail,
                    true
                ), this
            )
        }
    }

    override fun isTrackEncodable(track: AudioTrack): Boolean {
        return true
    }

    @Throws(IOException::class)
    override fun decodeTrack(trackInfo: AudioTrackInfo, input: DataInput): AudioTrack {
        return TwitchStreamAudioTrack(trackInfo, this)
    }

    override fun shutdown() {
        ExceptionTools.closeWithWarnings(httpInterfaceManager)
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
                // Get access token by channel name
                val request = createGetRequest("https://api.twitch.tv/api/channels/$name/access_token")
                return HttpClientTools.fetchResponseAsJson(httpInterface, request)
            }
        } catch (e: IOException) {
            throw FriendlyException(
                "Loading Twitch channel access token failed.",
                FriendlyException.Severity.SUSPICIOUS,
                e
            )
        }
    }

    private fun fetchStreamChannelInfo(channelId: String?): JsonBrowser? {
        try {
            httpInterface.use { httpInterface ->
                // helix/streams?user_login=name
                val request = createGetRequest("https://api.twitch.tv/kraken/streams/$channelId?stream_type=all")
                return HttpClientTools.fetchResponseAsJson(httpInterface, request)
            }
        } catch (e: IOException) {
            throw FriendlyException("Loading Twitch channel information failed.", FriendlyException.Severity.SUSPICIOUS, e)
        }
    }
}
