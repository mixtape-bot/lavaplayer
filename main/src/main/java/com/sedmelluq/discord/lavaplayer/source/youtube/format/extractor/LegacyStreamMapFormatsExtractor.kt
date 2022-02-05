package com.sedmelluq.discord.lavaplayer.source.youtube.format.extractor

import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeTrackJsonData
import com.sedmelluq.discord.lavaplayer.source.youtube.format.YoutubeTrackFormat
import com.sedmelluq.discord.lavaplayer.source.youtube.format.YoutubeTrackFormatExtractor
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools.decodeUrlEncodedItems
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools.extractBetween
import mu.KotlinLogging
import org.apache.http.entity.ContentType

class LegacyStreamMapFormatsExtractor : OfflineYoutubeTrackFormatExtractor() {
    companion object {
        private val log = KotlinLogging.logger {  }
    }

    override fun extract(data: YoutubeTrackJsonData): List<YoutubeTrackFormat> {
        val formatStreamMap = data.polymerArguments["url_encoded_fmt_stream_map"].text
            ?: return emptyList()

        return loadTrackFormatsFromFormatStreamMap(formatStreamMap)
    }

    private fun loadTrackFormatsFromFormatStreamMap(adaptiveFormats: String): List<YoutubeTrackFormat> {
        val tracks = mutableListOf<YoutubeTrackFormat>()

        var anyFailures = false
        for (formatString in adaptiveFormats.split(",")) {
            try {
                val format = decodeUrlEncodedItems(formatString, false)
                val url = format["url"]
                    ?: continue

                val contentLength = extractBetween(url, "clen=", "&")
                if (contentLength == null) {
                    log.debug { "Could not find content length from URL $url, skipping format" }
                    continue
                }

                tracks.add(YoutubeTrackFormat(
                    url,
                    qualityToBitrateValue(format["quality"]),
                    ContentType.parse(format["type"]),
                    contentLength.toLong(),
                    format["s"],
                    format["sp"] ?: YoutubeTrackFormatExtractor.DEFAULT_SIGNATURE_KEY
                ))
            } catch (e: RuntimeException) {
                anyFailures = true
                log.debug(e) { "Failed to parse format $formatString, skipping." }
            }
        }

        if (tracks.isEmpty() && anyFailures) {
            log.warn { "In adaptive format map $adaptiveFormats, all formats either failed to load or were skipped due to missing fields" }
        }

        return tracks
    }

    private fun qualityToBitrateValue(quality: String?): Long {
        // Return negative bitrate values to indicate missing bitrate info, but still retain the relative order.
        return when (quality) {
            "small" -> -10
            "medium" -> -5
            "hd720" -> -4
            else -> -1
        }
    }
}
