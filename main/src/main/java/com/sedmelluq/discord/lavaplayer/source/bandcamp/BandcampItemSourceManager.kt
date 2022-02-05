package com.sedmelluq.discord.lavaplayer.source.bandcamp

import com.sedmelluq.discord.lavaplayer.source.ItemSourceManager
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools
import com.sedmelluq.discord.lavaplayer.tools.extensions.closeWithWarnings
import com.sedmelluq.discord.lavaplayer.tools.io.*
import com.sedmelluq.discord.lavaplayer.tools.json.JsonTools
import com.sedmelluq.discord.lavaplayer.track.AudioItem
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.collection.Playlist
import com.sedmelluq.discord.lavaplayer.track.loader.LoaderState
import com.sedmelluq.lava.common.tools.exception.FriendlyException
import com.sedmelluq.lava.common.tools.exception.friendlyError
import com.sedmelluq.lava.common.tools.exception.wrapUnfriendlyException
import com.sedmelluq.lava.track.info.AudioTrackInfo
import com.sedmelluq.lava.track.info.BasicAudioTrackInfo
import org.apache.http.HttpStatus
import org.apache.http.client.methods.HttpGet
import org.apache.http.util.EntityUtils
import java.io.DataInput
import java.io.IOException
import java.util.regex.Pattern

/**
 * Audio source manager that implements finding Bandcamp tracks based on URL.
 */
class BandcampItemSourceManager : ItemSourceManager, HttpConfigurable {
    private val httpInterfaceManager: HttpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager()

    override val sourceName: String
        get() = "bandcamp"

    /**
     * @return Get an HTTP interface for a playing track.
     */
    val httpInterface: HttpInterface
        get() = httpInterfaceManager.get()

    override suspend fun loadItem(state: LoaderState, reference: AudioReference): AudioItem? {
        val urlInfo = parseUrl(reference.identifier!!)
        return if (urlInfo != null) {
            if (urlInfo.isAlbum) {
                loadAlbum(urlInfo)
            } else {
                loadTrack(urlInfo)
            }
        } else {
            null
        }
    }

    override fun isTrackEncodable(track: AudioTrack): Boolean {
        return true
    }

    @Throws(IOException::class)
    override fun decodeTrack(trackInfo: AudioTrackInfo, input: DataInput, version: Int): AudioTrack {
        return BandcampAudioTrack(trackInfo, this)
    }

    override fun shutdown() {
        httpInterfaceManager.closeWithWarnings()
    }

    override fun configureRequests(configurator: RequestConfigurator) {
        httpInterfaceManager.configureRequests(configurator)
    }

    override fun configureBuilder(configurator: BuilderConfigurator) {
        httpInterfaceManager.configureBuilder(configurator)
    }

    @Throws(IOException::class)
    internal fun readTrackListInformation(text: String?): BandcampTrackListModel {
        val trackInfoJson = DataFormatTools.extractBetween(text!!, "data-tralbum=\"", "\"")
            ?: friendlyError("Track information not found on the Bandcamp page.", FriendlyException.Severity.SUSPICIOUS)

        return JsonTools.decode(trackInfoJson.replace("&quot;", "\""))
    }

    private fun parseUrl(url: String): UrlInfo? {
        val matcher = urlRegex.matcher(url)
        return if (matcher.matches()) {
            UrlInfo(url, matcher.group(1), "album" == matcher.group(2))
        } else {
            null
        }
    }

    private fun loadTrack(urlInfo: UrlInfo): AudioItem? {
        return extractFromPage(urlInfo.fullUrl) { _, text ->
            val trackList = readTrackListInformation(text)

            extractTrack(trackList.trackInfo.first(), trackList, urlInfo.baseUrl)
        }
    }

    private fun loadAlbum(urlInfo: UrlInfo): AudioItem? {
        return extractFromPage(urlInfo.fullUrl) { _, text ->
            val trackList = readTrackListInformation(text)
            val tracks: MutableList<AudioTrack> = trackList.trackInfo
                .map { extractTrack(it, trackList, urlInfo.baseUrl) }
                .toMutableList()

            val albumInfo = readAlbumInformation(text)
            Playlist(albumInfo.current.title, tracks, isAlbum = true)
        }
    }

    private fun extractTrack(
        trackInfo: BandcampTrackModel,
        trackList: BandcampTrackListModel,
        bandUrl: String
    ): AudioTrack {
        val info = BasicAudioTrackInfo(
            title = trackInfo.title,
            author = trackList.artist,
            length = (trackInfo.duration * 1000.0).toLong(),
            uri = bandUrl + trackInfo.titleLink,
            identifier = trackInfo.id,
            artworkUrl = trackList.artworkUrl,
        )

        return BandcampAudioTrack(info, this)
    }

    @Throws(IOException::class)
    private fun readAlbumInformation(text: String?): BandcampTrackListModel {
        val albumInfoJson = DataFormatTools.extractBetween(text!!, "data-tralbum=\"", "\"")
            ?: friendlyError("Album information not found on the Bandcamp page.", FriendlyException.Severity.SUSPICIOUS)

        return JsonTools.decode(albumInfoJson.replace("&quot;", "\""))
    }

    private fun extractFromPage(url: String?, extractor: AudioItemExtractor): AudioItem? {
        try {
            httpInterfaceManager
                .get()
                .use { httpInterface -> return extractFromPageWithInterface(httpInterface, url, extractor) }
        } catch (e: Exception) {
            throw e.wrapUnfriendlyException("Loading information for a Bandcamp track failed.", FriendlyException.Severity.FAULT)
        }
    }

    @Throws(Exception::class)
    private fun extractFromPageWithInterface(
        httpInterface: HttpInterface,
        url: String?,
        extractor: AudioItemExtractor
    ): AudioItem? {
        httpInterface.execute(HttpGet(url)).use { response ->
            val statusCode = response.statusLine.statusCode
            if (statusCode == HttpStatus.SC_NOT_FOUND) {
                return AudioReference(null, null)
            } else if (!HttpClientTools.isSuccessWithContent(statusCode)) {
                throw IOException("Invalid status code for track page: $statusCode")
            }

            val responseText = EntityUtils.toString(response.entity, Charsets.UTF_8)
            return extractor.extract(httpInterface, responseText)
        }
    }

    private fun interface AudioItemExtractor {
        @Throws(Exception::class)
        fun extract(httpInterface: HttpInterface, text: String?): AudioItem?
    }

    private data class UrlInfo(val fullUrl: String?, val baseUrl: String, val isAlbum: Boolean)

    companion object {
        private const val URL_REGEX =
            "^(https?://(?:[^.]+\\.|)bandcamp\\.com)/(track|album)/([a-zA-Z0-9-_]+)/?(?:\\?.*|)$"
        private val urlRegex = Pattern.compile(URL_REGEX)
    }

}
