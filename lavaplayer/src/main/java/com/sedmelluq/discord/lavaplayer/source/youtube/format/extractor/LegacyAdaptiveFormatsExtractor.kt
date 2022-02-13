package com.sedmelluq.discord.lavaplayer.source.youtube.format.extractor

import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeTrackJsonData
import com.sedmelluq.discord.lavaplayer.source.youtube.format.YoutubeTrackFormat
import com.sedmelluq.discord.lavaplayer.source.youtube.format.YoutubeTrackFormatExtractor.Companion.DEFAULT_SIGNATURE_KEY
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools.decodeUrlEncodedItems
import org.apache.http.entity.ContentType

class LegacyAdaptiveFormatsExtractor : OfflineYoutubeTrackFormatExtractor() {
    override fun extract(data: YoutubeTrackJsonData): List<YoutubeTrackFormat> {
        val adaptiveFormats = data.polymerArguments["adaptive_fmts"].text
            ?: return emptyList()

        return loadTrackFormatsFromAdaptive(adaptiveFormats)
    }

    private fun loadTrackFormatsFromAdaptive(adaptiveFormats: String) = adaptiveFormats
        .split(",")
        .map { formatString ->
            val format = decodeUrlEncodedItems(formatString, false)

            YoutubeTrackFormat(
                format["url"]!!,
                format["bitrate"]!!.toLong(),
                ContentType.parse(format["type"]),
                format["clen"]!!.toLong(),
                format["s"]!!,
                format.getOrDefault("sp", DEFAULT_SIGNATURE_KEY)
            )
        }
}
