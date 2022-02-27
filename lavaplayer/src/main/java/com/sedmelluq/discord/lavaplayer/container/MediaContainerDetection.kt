package com.sedmelluq.discord.lavaplayer.container

import com.sedmelluq.discord.lavaplayer.tools.io.GreedyInputStream
import com.sedmelluq.discord.lavaplayer.tools.io.SavedHeadSeekableInputStream
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.lava.common.tools.exception.FriendlyException
import com.sedmelluq.lava.common.tools.exception.wrapUnfriendlyException
import mu.KotlinLogging
import java.io.IOException
import java.nio.charset.Charset
import java.util.regex.Pattern

/**
 * Detects the container used by a file and whether the specific file is supported for playback.
 *
 * @param reference   Reference to the track with an identifier, used in the AudioTrackInfo in result
 * @param inputStream Input stream of the file
 * @param hints       Hints about the format (mime type, extension)
 */
class MediaContainerDetection(
    private val containerRegistry: MediaContainerRegistry, private val reference: AudioReference,
    private val inputStream: SeekableInputStream, private val hints: MediaContainerHints
) {
    /**
     * @return Result of detection.
     */
    fun detectContainer(): MediaContainerDetectionResult {
        val result: MediaContainerDetectionResult?
        try {
            val savedHeadInputStream = SavedHeadSeekableInputStream(inputStream, HEAD_MARK_LIMIT)
            savedHeadInputStream.loadHead()

            result = detectContainer(savedHeadInputStream, true) ?: detectContainer(savedHeadInputStream, false)
        } catch (e: Exception) {
            throw e.wrapUnfriendlyException("Could not read the file for detecting file type.", FriendlyException.Severity.SUSPICIOUS)
        }

        return result ?: MediaContainerDetectionResult.UNKNOWN_FORMAT
    }

    @Throws(IOException::class)
    private fun detectContainer(innerStream: SeekableInputStream, matchHints: Boolean): MediaContainerDetectionResult? {
        for (probe in containerRegistry.probes) {
            if (matchHints == probe.matchesHints(hints)) {
                innerStream.seek(0)
                return checkContainer(probe, reference, innerStream) ?: continue
            }
        }

        return null
    }

    companion object {
        private const val HEAD_MARK_LIMIT = 1024

        private val log = KotlinLogging.logger { }

        const val UNKNOWN_TITLE = "Unknown title"
        const val UNKNOWN_ARTIST = "Unknown artist"
        const val STREAM_SCAN_DISTANCE = 1000

        private fun checkContainer(
            probe: MediaContainerProbe, reference: AudioReference,
            inputStream: SeekableInputStream
        ): MediaContainerDetectionResult? {
            return try {
                probe.probe(reference, inputStream)
            } catch (e: Exception) {
                log.warn(e) { "Attempting to detect file with container ${probe.name} failed." }
                null
            }
        }

        /**
         * Checks the next bytes in the stream if they match the specified bytes. The input may contain -1 as byte value as
         * a wildcard, which means the value of this byte does not matter. The position of the stream is restored on return.
         *
         * @param stream Input stream to read the bytes from
         * @param match  Bytes that the next bytes from input stream should match (-1 as wildcard
         * @param rewind If set to true, restores the original position of the stream after checking
         *
         * @return True if the bytes matched
         * @throws IOException On IO error
         */
        @JvmStatic
        @JvmOverloads
        @Throws(IOException::class)
        fun checkNextBytes(stream: SeekableInputStream, match: IntArray, rewind: Boolean = true): Boolean {
            val position = stream.position
            var result = true
            for (matchByte in match) {
                val inputByte = stream.read()
                if (inputByte == -1 || (matchByte != -1 && matchByte != inputByte)) {
                    result = false
                    break
                }
            }

            if (rewind) {
                stream.seek(position)
            }

            return result
        }

        /**
         * Check if the next bytes in the stream match the specified regex pattern.
         *
         * @param stream   Input stream to read the bytes from
         * @param distance Maximum number of bytes to read for matching
         * @param pattern  Pattern to match against
         * @param charset  Charset to use to decode the bytes
         * @return True if the next bytes in the stream are a match
         * @throws IOException On read error
         */
        @Throws(IOException::class)
        fun matchNextBytesAsRegex(
            stream: SeekableInputStream,
            distance: Int,
            pattern: Pattern,
            charset: Charset = Charsets.UTF_8
        ): Boolean {
            val position = stream.position
            val bytes = ByteArray(distance)
            val read = GreedyInputStream(stream).read(bytes)

            stream.seek(position)
            if (read == -1) {
                return false
            }

            val text = String(bytes, 0, read, charset)
            return pattern.matcher(text).find()
        }
    }
}
