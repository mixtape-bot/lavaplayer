package com.sedmelluq.discord.lavaplayer.source.vimeo

import com.sedmelluq.discord.lavaplayer.source.ItemSourceManager
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools
import com.sedmelluq.discord.lavaplayer.tools.extensions.closeWithWarnings
import com.sedmelluq.discord.lavaplayer.tools.io.*
import com.sedmelluq.discord.lavaplayer.tools.json.JsonTools
import com.sedmelluq.discord.lavaplayer.track.AudioItem
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.loader.LoaderState
import com.sedmelluq.lava.common.tools.exception.FriendlyException
import com.sedmelluq.lava.common.tools.exception.friendlyError
import com.sedmelluq.lava.track.info.AudioTrackInfo
import com.sedmelluq.lava.track.info.BasicAudioTrackInfo
import org.apache.http.HttpStatus
import org.apache.http.client.methods.HttpGet
import org.apache.http.util.EntityUtils
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException
import java.util.regex.Pattern

/**
 * Audio source manager which detects Vimeo tracks by URL.
 */
class VimeoItemSourceManager : ItemSourceManager, HttpConfigurable {
    companion object {
        private const val TRACK_URL_REGEX = """^https://vimeo.com/\d+(?:\?.*)?$"""
        private val trackUrlPattern = Pattern.compile(TRACK_URL_REGEX)
    }

    private val httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager()

    /**
     * Get an HTTP interface for a playing track.
     */
    val httpInterface: HttpInterface
        get() = httpInterfaceManager.get()

    override val sourceName: String
        get() = "vimeo"

    override suspend fun loadItem(state: LoaderState, reference: AudioReference): AudioItem? {
        if (!trackUrlPattern.matcher(reference.identifier).matches()) {
            return null
        }

        try {
            httpInterface.use { return loadFromTrackPage(it, reference.identifier) }
        } catch (e: IOException) {
            friendlyError("Loading Vimeo track information failed.", FriendlyException.Severity.SUSPICIOUS, e)
        }
    }

    override fun isTrackEncodable(track: AudioTrack): Boolean = true

    override fun shutdown() = httpInterfaceManager.closeWithWarnings()

    @Throws(IOException::class)
    override fun encodeTrack(track: AudioTrack, output: DataOutput, version: Int) = // Nothing special to encode
        Unit

    @Throws(IOException::class)
    override fun decodeTrack(trackInfo: AudioTrackInfo, input: DataInput, version: Int): AudioTrack =
        VimeoAudioTrack(trackInfo, this)

    override fun configureRequests(configurator: RequestConfigurator) =
        httpInterfaceManager.configureRequests(configurator)

    override fun configureBuilder(configurator: BuilderConfigurator) =
        httpInterfaceManager.configureBuilder(configurator)

    internal fun loadConfigJsonFromPageContent(content: String): VimeoClipPage? {
        return DataFormatTools.extractBetween(content, "window.vimeo.clip_page_config = ", "\n")
            ?.removeSuffix(";")
            ?.let { JsonTools.decode(it) }
    }

    private fun loadFromTrackPage(httpInterface: HttpInterface, trackUrl: String?): AudioItem {
        httpInterface.execute(HttpGet(trackUrl)).use { response ->
            val statusCode = response.statusLine.statusCode
            if (statusCode == HttpStatus.SC_NOT_FOUND) {
                return AudioReference.NO_TRACK
            } else if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                friendlyError("Server responded with an error.", FriendlyException.Severity.SUSPICIOUS, IllegalStateException("Response code is $statusCode"))
            }

            val pageContent = EntityUtils.toString(response.entity, Charsets.UTF_8)
            return loadTrackFromPageContent(trackUrl, pageContent)
        }
    }

    private fun loadTrackFromPageContent(trackUrl: String?, content: String): AudioTrack {
        val config = loadConfigJsonFromPageContent(content)
            ?: friendlyError("Track information not found on the page.", FriendlyException.Severity.SUSPICIOUS)

        val info = BasicAudioTrackInfo(
            title = config.clip.title,
            author = config.owner.displayName,
            length = (config.clip.duration.raw * 1000.0).toLong(),
            identifier = trackUrl!!,
            uri = trackUrl,
            artworkUrl = config.thumbnail.src
        )

        return VimeoAudioTrack(info, this)
    }
}
