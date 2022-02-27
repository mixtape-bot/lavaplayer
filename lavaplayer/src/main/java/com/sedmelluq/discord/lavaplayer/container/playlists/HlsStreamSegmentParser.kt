package com.sedmelluq.discord.lavaplayer.container.playlists

import com.sedmelluq.discord.lavaplayer.container.playlists.ExtendedM3uParser.parseLine
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools.fetchResponseLines
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import org.apache.http.client.methods.HttpGet
import java.io.IOException
import kotlin.math.roundToLong

object HlsStreamSegmentParser {
    @Throws(IOException::class)
    fun parseFromUrl(httpInterface: HttpInterface, url: String): MutableList<HlsStreamSegment> {
        return parseFromLines(fetchResponseLines(httpInterface, HttpGet(url), "stream segments list"))
    }

    private fun parseFromLines(lines: List<String>): MutableList<HlsStreamSegment> {
        val segments: MutableList<HlsStreamSegment> = mutableListOf()
        var segmentInfo: ExtendedM3uParser.Line? = null
        for (lineText in lines) {
            val line = parseLine(lineText)
            if (line.isDirective && "EXTINF" == line.directiveName) {
                segmentInfo = line
            }

            if (line.isData) {
                val segment = if (segmentInfo != null && segmentInfo.extraData!!.contains(",")) {
                    val (duration, name) = segmentInfo.extraData!!.split(",", limit = 2)
                    HlsStreamSegment(line.lineData!!, parseSecondDuration(duration), name)
                } else {
                    HlsStreamSegment(line.lineData!!, null, null)
                }

                segments.add(segment)
            }
        }

        return segments
    }

    private fun parseSecondDuration(value: String): Long? {
        return value.runCatching { (toDouble() * 1000.0).roundToLong() }
            .getOrNull()
    }
}
