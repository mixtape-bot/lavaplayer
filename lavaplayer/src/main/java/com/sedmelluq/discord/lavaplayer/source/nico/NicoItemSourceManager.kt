package com.sedmelluq.discord.lavaplayer.source.nico

import com.sedmelluq.discord.lavaplayer.source.ItemSourceManager
import com.sedmelluq.discord.lavaplayer.tools.extensions.UrlEncodedFormEntity
import com.sedmelluq.discord.lavaplayer.tools.extensions.parseMilliseconds
import com.sedmelluq.discord.lavaplayer.tools.io.*
import com.sedmelluq.discord.lavaplayer.track.AudioItem
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.loader.LoaderState
import com.sedmelluq.lava.common.tools.exception.FriendlyException
import com.sedmelluq.lava.common.tools.exception.friendlyError
import com.sedmelluq.lava.track.info.AudioTrackInfo
import com.sedmelluq.lava.track.info.BasicAudioTrackInfo
import kotlinx.atomicfu.atomic
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.io.DataInput
import java.io.IOException
import java.util.regex.Pattern

/**
 * Audio source manager that implements finding NicoNico tracks based on URL.
 */
class NicoItemSourceManager(private val email: String, private val password: String) : ItemSourceManager, HttpConfigurable {
    companion object {
        private const val TRACK_URL_REGEX = """^https?://(?:www\.)?nicovideo\.jp/watch/(sm[0-9]+)(?:\?.*)?$"""
        private val trackUrlPattern = Pattern.compile(TRACK_URL_REGEX)
        private fun getWatchUrl(videoId: String): String = "http://www.nicovideo.jp/watch/$videoId"
    }

    private val httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager()
    private var loggedIn by atomic(false)

    override val sourceName: String =
        "niconico"

    /**
     * @return Get an HTTP interface for a playing track.
     */
    val httpInterface: HttpInterface
        get() = httpInterfaceManager.get()

    fun checkLoggedIn() = synchronized(loggedIn) {
        if (loggedIn) {
            return
        }

        val loginRequest = HttpPost("https://secure.nicovideo.jp/secure/login")
        loginRequest.entity = UrlEncodedFormEntity("mail" to email, "password" to password, charset = Charsets.UTF_8)

        try {
            httpInterface.use { httpInterface ->
                httpInterface.execute(loginRequest).use { response ->
                    val statusCode = response.statusLine.statusCode.takeUnless { it != 302 }
                    if (statusCode != 302) {
                        throw IOException("Unexpected response code $statusCode")
                    }

                    response.getFirstHeader("Location")?.takeUnless { it.value.contains("message=") }
                        ?: friendlyError("Login details for NicoNico are invalid.")

                    loggedIn = true
                }
            }
        } catch (e: IOException) {
            friendlyError("Exception when trying to log into NicoNico", FriendlyException.Severity.SUSPICIOUS, e)
        }
    }

    override fun isTrackEncodable(track: AudioTrack): Boolean =
        true

    @Throws(IOException::class)
    override fun decodeTrack(trackInfo: AudioTrackInfo, input: DataInput, version: Int): AudioTrack =
        NicoAudioTrack(trackInfo, this)

    override fun configureRequests(configurator: RequestConfigurator) =
        httpInterfaceManager.configureRequests(configurator)

    override fun configureBuilder(configurator: BuilderConfigurator) =
        httpInterfaceManager.configureBuilder(configurator)

    override suspend fun loadItem(state: LoaderState, reference: AudioReference): AudioItem? {
        val trackMatcher = trackUrlPattern.matcher(reference.identifier)
        return if (trackMatcher.matches()) loadTrack(trackMatcher.group(1)) else null
    }

    private fun loadTrack(videoId: String): AudioTrack {
        checkLoggedIn()
        try {
            httpInterface.use { httpInterface ->
                httpInterface.execute(HttpGet("http://ext.nicovideo.jp/api/getthumbinfo/$videoId")).use { response ->
                    val statusCode = response.statusLine.statusCode
                    if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                        throw IOException("Unexpected response code from video info: $statusCode")
                    }

                    val document =
                        Jsoup.parse(response.entity.content, Charsets.UTF_8.name(), "", Parser.xmlParser())

                    return extractTrackFromXml(videoId, document)!!
                }
            }
        } catch (e: IOException) {
            friendlyError("Error occurred when extracting video info.", FriendlyException.Severity.SUSPICIOUS, e)
        }
    }

    private fun extractTrackFromXml(videoId: String, document: Document): AudioTrack? {
        for (element in document.select(":root > thumb")) {
            val trackInfo = BasicAudioTrackInfo(
                title = element.select("title").first()!!.text(),
                author  = element.select("user_nickname").first()!!.text(),
                length = element.select("length").first()!!.text().parseMilliseconds(),
                identifier = videoId,
                uri = getWatchUrl(videoId),
                artworkUrl = element.select("thumbnail_url").first()!!.text(),
                isStream = false
            )

            return NicoAudioTrack(trackInfo, this)
        }

        return null
    }
}
