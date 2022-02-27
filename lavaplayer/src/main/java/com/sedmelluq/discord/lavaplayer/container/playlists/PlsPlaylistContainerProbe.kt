package com.sedmelluq.discord.lavaplayer.container.playlists

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetection
import com.sedmelluq.discord.lavaplayer.container.MediaContainerDetectionResult
import com.sedmelluq.discord.lavaplayer.container.MediaContainerHints
import com.sedmelluq.discord.lavaplayer.container.MediaContainerProbe
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.lava.track.info.AudioTrackInfo
import mu.KotlinLogging
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.regex.Pattern

/**
 * Probe for PLS playlist.
 */
class PlsPlaylistContainerProbe : MediaContainerProbe {
    companion object {
        private val log = KotlinLogging.logger {  }
        private val PLS_HEADER = intArrayOf('['.code, -1, 'l'.code, 'a'.code, 'y'.code, 'l'.code, 'i'.code, 's'.code, 't'.code, ']'.code)
        private val FILE_PATTERN = Pattern.compile("\\s*File([0-9]+)=((?:https?|icy)://.*)\\s*")
        private val TITLE_PATTERN = Pattern.compile("\\s*Title([0-9]+)=(.*)\\s*")
    }

    override val name: String
        get() = "pls"

    override fun matchesHints(hints: MediaContainerHints?): Boolean {
        return false
    }

    @Throws(IOException::class)
    override fun probe(reference: AudioReference, inputStream: SeekableInputStream): MediaContainerDetectionResult? {
        if (!MediaContainerDetection.checkNextBytes(inputStream, PLS_HEADER)) {
            return null
        }

        log.debug { "Track ${reference.identifier} is a PLS playlist file." }
        return loadFromLines(DataFormatTools.streamToLines(inputStream))
    }

    private fun loadFromLines(lines: List<String>): MediaContainerDetectionResult {
        val trackFiles: MutableMap<String, String> = mutableMapOf()
        val trackTitles: MutableMap<String, String> = mutableMapOf()

        for (line in lines) {
            val fileMatcher = FILE_PATTERN.matcher(line)
            if (fileMatcher.matches()) {
                trackFiles[fileMatcher.group(1)] = fileMatcher.group(2)
                continue
            }

            val titleMatcher = TITLE_PATTERN.matcher(line)
            if (titleMatcher.matches()) {
                trackTitles[titleMatcher.group(1)] = titleMatcher.group(2)
            }
        }

        val (key, value) = trackFiles.entries.firstOrNull()
            ?: return MediaContainerDetectionResult.unsupportedFormat(this, "The playlist file contains no links.")

        val title = trackTitles[key]
            ?: MediaContainerDetection.UNKNOWN_TITLE

        return MediaContainerDetectionResult.refer(this, AudioReference(value, title))
    }

    override fun createTrack(parameters: String?, trackInfo: AudioTrackInfo, inputStream: SeekableInputStream, ): AudioTrack {
        throw UnsupportedOperationException()
    }
}
