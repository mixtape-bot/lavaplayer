package com.sedmelluq.discord.lavaplayer.source.youtube.format.extractor

import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeTrackJsonData
import com.sedmelluq.discord.lavaplayer.source.youtube.format.YoutubeTrackFormat
import com.sedmelluq.discord.lavaplayer.source.youtube.format.YoutubeTrackFormatExtractor
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools.decodeUrlEncodedItems
import com.sedmelluq.discord.lavaplayer.tools.Units
import com.sedmelluq.discord.lavaplayer.tools.json.JsonTools
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.LongAsStringSerializer
import kotlinx.serialization.encodeToString
import mu.KotlinLogging
import org.apache.http.entity.ContentType

class StreamingDataFormatsExtractor : OfflineYoutubeTrackFormatExtractor() {
    companion object {
        private val log = KotlinLogging.logger {  }
    }

    override fun extract(data: YoutubeTrackJsonData): List<YoutubeTrackFormat> {
        val streamingData = data.playerResponse["streamingData"].takeUnless { it.isNull }?.cast<StreamingData>()
            ?: return emptyList()

        val isLive = data.playerResponse["videoDetails"]["isLive"].asBoolean(false)
        val formats = loadTrackFormatsFromStreamingData(streamingData.formats, isLive)
        formats.addAll(loadTrackFormatsFromStreamingData(streamingData.adaptiveFormats, isLive))

        return formats
    }

    private fun loadTrackFormatsFromStreamingData(formats: List<StreamingData.Format>?, isLive: Boolean): MutableList<YoutubeTrackFormat> {
        val tracks = mutableListOf<YoutubeTrackFormat>()
        var anyFailures = false
        if (formats != null) {
            for (format in formats) {
                val cipherInfo = (format.cipher ?: format.signatureCipher)
                    ?.let { decodeUrlEncodedItems(it, true) }
                    ?: emptyMap()

                try {
                    if (format.contentLength == null && !isLive) {
                        val json = JsonTools.format.encodeToString(format)
                        log.debug { "Track not a live stream, but no contentLength in format $json skipping" }

                        continue
                    }

                    val youtubeFormat = YoutubeTrackFormat(
                        baseUrl = cipherInfo["url"] ?: format.url,
                        bitrate = format.bitrate,
                        contentType = ContentType.parse(format.mimeType),
                        contentLength = format.contentLength ?: Units.CONTENT_LENGTH_UNKNOWN,
                        signature = cipherInfo["s"],
                        signatureKey = cipherInfo["sp"] ?: YoutubeTrackFormatExtractor.DEFAULT_SIGNATURE_KEY
                    )

                    tracks.add(youtubeFormat)
                } catch (e: RuntimeException) {
                    anyFailures = true
                    log.debug(e) { "Failed to parse format $format, skipping" }
                }
            }
        }

        if (tracks.isEmpty() && anyFailures) {
            val json = JsonTools.format.encodeToString(formats)
            log.warn { "In streamingData adaptive formats $json, all formats either failed to load or were skipped due to missing fields" }
        }

        return tracks
    }

    @Serializable
    data class StreamingData(val formats: List<Format>?, val adaptiveFormats: List<Format>?, ) {
        @Serializable
        data class Format(
            val url: String,
            val cipher: String? = null,
            val signatureCipher: String? = null,
            @Serializable(with = LongAsStringSerializer::class)
            val contentLength: Long? = null,
            val mimeType: String,
            @Serializable(with = LongAsStringSerializer::class)
            val bitrate: Long = Units.BITRATE_UNKNOWN
        )
    }
}
