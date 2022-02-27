package com.sedmelluq.discord.lavaplayer.source.http

import com.sedmelluq.discord.lavaplayer.container.*
import com.sedmelluq.discord.lavaplayer.source.ProbingItemSourceManager
import com.sedmelluq.discord.lavaplayer.tools.Units
import com.sedmelluq.discord.lavaplayer.tools.extensions.create
import com.sedmelluq.discord.lavaplayer.tools.io.*
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools.NoRedirectsStrategy
import com.sedmelluq.discord.lavaplayer.track.AudioItem
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.loader.LoaderState
import com.sedmelluq.lava.common.tools.exception.FriendlyException
import com.sedmelluq.lava.common.tools.exception.friendlyError
import com.sedmelluq.lava.track.info.AudioTrackInfo
import com.sedmelluq.lava.track.info.AudioTrackInfoBuilder
import org.apache.http.HttpStatus
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException

/**
 * Audio source manager which implements finding audio files from HTTP addresses.
 */
class HttpItemSourceManager @JvmOverloads constructor(
    containerRegistry: MediaContainerRegistry = MediaContainerRegistry.DEFAULT_REGISTRY
) : HttpConfigurable, ProbingItemSourceManager(containerRegistry) {
    companion object {
        @JvmStatic
        fun getAsHttpReference(reference: AudioReference): AudioReference? = when {
            reference.uri!!.startsWith("https://") || reference.identifier!!.startsWith("http://") ->
                reference

            reference.uri!!.startsWith("icy://") ->
                AudioReference("http://" + reference.identifier!!.substring(6), reference.title)

            else -> null
        }
    }

    private val httpInterfaceManager: HttpInterfaceManager = ThreadLocalHttpInterfaceManager(
        HttpClientTools
            .createSharedCookiesHttpBuilder()
            .setRedirectStrategy(NoRedirectsStrategy()),
        HttpClientTools.DEFAULT_REQUEST_CONFIG
    )

    override val sourceName: String
        get() = "http"

    /**
     * An HTTP interface for a playing track.
     */
    internal val httpInterface: HttpInterface
        get() = httpInterfaceManager.get()

    // Nothing to shut down
    override fun shutdown() = Unit

    override fun isTrackEncodable(track: AudioTrack): Boolean = true

    override suspend fun loadItem(state: LoaderState, reference: AudioReference): AudioItem? {
        val httpReference = getAsHttpReference(reference)
            ?: return null

        return if (httpReference.containerDescriptor != null) {
            createTrack(AudioTrackInfoBuilder.create(reference, null).build(), httpReference.containerDescriptor)
        } else {
            handleLoadResult(detectContainer(httpReference))
        }
    }

    override fun createTrack(trackInfo: AudioTrackInfo, containerDescriptor: MediaContainerDescriptor): AudioTrack =
        HttpAudioTrack(trackInfo, containerDescriptor, this)

    override fun configureRequests(configurator: RequestConfigurator) =
        httpInterfaceManager.configureRequests(configurator)

    override fun configureBuilder(configurator: BuilderConfigurator) =
        httpInterfaceManager.configureBuilder(configurator)

    @Throws(IOException::class)
    override fun encodeTrack(track: AudioTrack, output: DataOutput, version: Int) =
        encodeTrackFactory((track as HttpAudioTrack).containerTrackFactory, output)

    @Throws(IOException::class)
    override fun decodeTrack(trackInfo: AudioTrackInfo, input: DataInput, version: Int): AudioTrack? =
        decodeTrackFactory(input)?.let { HttpAudioTrack(trackInfo, it, this) }

    private fun detectContainer(reference: AudioReference): MediaContainerDetectionResult? {
        var result: MediaContainerDetectionResult?
        try {
            httpInterface.use { result = detectContainerWithClient(it, reference) }
        } catch (e: IOException) {
            friendlyError("Connecting to the URL failed.", FriendlyException.Severity.SUSPICIOUS, e)
        }

        return result
    }

    @Throws(IOException::class)
    private fun detectContainerWithClient(httpInterface: HttpInterface, reference: AudioReference): MediaContainerDetectionResult? = try {
        PersistentHttpStream(httpInterface, URI(reference.identifier), Units.CONTENT_LENGTH_UNKNOWN).use { inputStream ->
            val statusCode = inputStream.checkStatusCode()
            val redirectUrl = HttpClientTools.getRedirectLocation(reference.identifier!!, inputStream.currentResponse!!)
            when {
                redirectUrl != null ->
                    return MediaContainerDetectionResult.refer(null, AudioReference(redirectUrl))

                statusCode == HttpStatus.SC_NOT_FOUND ->
                    return null

                !HttpClientTools.isSuccessWithContent(statusCode) ->
                    friendlyError("That URL is not playable.", cause = IllegalStateException("Status code $statusCode"))
            }

            val hints = MediaContainerHints.from(
                mimeType = HttpClientTools.getHeaderValue(inputStream.currentResponse, "Content-Type"),
                fileExtension = null
            )

            return MediaContainerDetection(containerRegistry, reference, inputStream, hints).detectContainer()
        }
    } catch (e: URISyntaxException) {
        friendlyError("Not a valid URL.", cause = e)
    }
}
