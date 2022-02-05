package com.sedmelluq.discord.lavaplayer.source.youtube

import com.sedmelluq.discord.lavaplayer.source.youtube.format.YoutubeTrackFormat
import com.sedmelluq.discord.lavaplayer.source.youtube.format.extractor.LegacyAdaptiveFormatsExtractor
import com.sedmelluq.discord.lavaplayer.source.youtube.format.extractor.LegacyDashMpdFormatsExtractor
import com.sedmelluq.discord.lavaplayer.source.youtube.format.extractor.LegacyStreamMapFormatsExtractor
import com.sedmelluq.discord.lavaplayer.source.youtube.format.extractor.StreamingDataFormatsExtractor
import com.sedmelluq.discord.lavaplayer.tools.ThumbnailTools
import com.sedmelluq.discord.lavaplayer.tools.Units
import com.sedmelluq.discord.lavaplayer.tools.Units.DURATION_MS_UNKNOWN
import com.sedmelluq.discord.lavaplayer.tools.extensions.toRuntimeException
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import com.sedmelluq.discord.lavaplayer.tools.json.JsonBrowser
import com.sedmelluq.lava.common.tools.exception.FriendlyException.Severity.SUSPICIOUS
import com.sedmelluq.lava.common.tools.exception.friendlyError
import com.sedmelluq.lava.track.info.AudioTrackInfo
import com.sedmelluq.lava.track.info.BasicAudioTrackInfo
import mu.KotlinLogging

class DefaultYoutubeTrackDetails(
    private val videoId: String,
    private val data: YoutubeTrackJsonData
) : YoutubeTrackDetails {
    companion object {
        private val log = KotlinLogging.logger { }
        private val FORMAT_EXTRACTORS = listOf(
            LegacyAdaptiveFormatsExtractor(),
            StreamingDataFormatsExtractor(),
            LegacyDashMpdFormatsExtractor(),
            LegacyStreamMapFormatsExtractor()
        )
    }

    override val trackInfo: AudioTrackInfo
        get() = loadTrackInfo()

    override val playerScript: String?
        get() = data.playerScriptUrl

    override fun getFormats(
        httpInterface: HttpInterface,
        signatureResolver: YoutubeSignatureResolver
    ): List<YoutubeTrackFormat> {
        try {
            return loadTrackFormats(httpInterface, signatureResolver)
        } catch (e: Exception) {
            throw e.toRuntimeException()
        }
    }

    private fun loadTrackFormats(
        httpInterface: HttpInterface,
        signatureResolver: YoutubeSignatureResolver
    ): List<YoutubeTrackFormat> {
        val formats = FORMAT_EXTRACTORS.firstNotNullOfOrNull {
            it.extract(data, httpInterface, signatureResolver)
                .takeUnless { formats -> formats.isEmpty() }
        }

        if (formats == null) {
            log.warn { "Video $videoId with no detected format field, response ${data.playerResponse.format()} polymer ${data.polymerArguments.format()}" }
            friendlyError(
                "Unable to play this YouTube track.",
                SUSPICIOUS,
                IllegalStateException("No track formats found.")
            )
        }

        return formats
    }

    private fun loadTrackInfo(): AudioTrackInfo {
        val playabilityStatus = data.playerResponse["playabilityStatus"]
        if ("ERROR" == playabilityStatus["status"].safeText) {
            friendlyError(playabilityStatus["reason"].text)
        }

        val videoDetails = data.playerResponse["videoDetails"].takeUnless { it.isNull }
            ?: return loadLegacyTrackInfo()

        val temporalInfo =
            TemporalInfo.fromRawData(videoDetails["isLiveContent"].cast(false), videoDetails["lengthSeconds"])
        val artworkUrl = ThumbnailTools.extractYouTube(videoDetails, videoId)

        return buildTrackInfo(
            videoId,
            videoDetails["title"].safeText,
            videoDetails["author"].safeText,
            temporalInfo,
            artworkUrl
        )
    }

    private fun loadLegacyTrackInfo(): AudioTrackInfo {
        val args = data.polymerArguments
        if ("fail" == args["status"].safeText) {
            friendlyError(args["reason"].safeText)
        }

        val temporalInfo = TemporalInfo.fromRawData(args["live_playback"].safeText == "1", args["length_seconds"])
        val artworkUrl = ThumbnailTools.extractYouTube(args, videoId)

        return buildTrackInfo(videoId, args["title"].safeText, args["author"].safeText, temporalInfo, artworkUrl)
    }

    private fun buildTrackInfo(
        videoId: String,
        title: String,
        uploader: String,
        temporalInfo: TemporalInfo,
        artworkUrl: String
    ): AudioTrackInfo {
        return BasicAudioTrackInfo(
            title = title,
            author = uploader,
            length = temporalInfo.durationMillis,
            identifier = videoId,
            uri = "${YoutubeConstants.WATCH_URL_PREFIX}$videoId",
            artworkUrl = artworkUrl,
            isStream = temporalInfo.isActiveStream
        )
    }

    data class TemporalInfo(val isActiveStream: Boolean, val durationMillis: Long) {
        companion object {
            fun fromRawData(wasLiveStream: Boolean, durationSecondsField: JsonBrowser): TemporalInfo {
                val durationValue = durationSecondsField.cast(0L)
                // VODs are not really live streams, even though that field in JSON claims they are. If it is actually live, then
                // duration is also missing or 0.
                val isActiveStream = wasLiveStream && durationValue == 0L

                return TemporalInfo(
                    isActiveStream,
                    DURATION_MS_UNKNOWN.takeIf { durationValue == 0L } ?: Units.secondsToMillis(durationValue))
            }
        }
    }
}
