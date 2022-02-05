package com.sedmelluq.discord.lavaplayer.tools

import org.apache.http.NameValuePair
import org.apache.http.client.utils.URLEncodedUtils
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset

typealias TextRange = Pair<String, String>

/**
 * Helper methods related to Strings, Maps, and Numbers.
 */
object DataFormatTools {
    /**
     * Consumes a stream and returns it as lines.
     *
     * @param inputStream Input stream to consume.
     * @param charset     Character set of the stream
     *
     * @return Lines from the stream
     * @throws IOException On read error
     */
    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class)
    fun streamToLines(inputStream: InputStream, charset: Charset = Charsets.UTF_8): List<String> =
        inputStream.reader(charset).readLines()

    fun extractBetween(haystack: String, candidates: List<TextRange>): String? =
        candidates.firstNotNullOfOrNull { extractBetween(haystack, it.first, it.second) }

    /**
     * Extract text between the first subsequent occurrences of start and end in haystack
     *
     * @param haystack The text to search from
     * @param start    The text after which to start extracting
     * @param end      The text before which to stop extracting
     * @return The extracted string
     */
    @JvmStatic
    fun extractBetween(haystack: String, start: String, end: String): String? {
        val startMatch = haystack.indexOf(start)
        if (startMatch >= 0) {
            val startPosition = startMatch + start.length
            val endPosition = haystack.indexOf(end, startPosition)
            if (endPosition >= 0) {
                return haystack.substring(startPosition, endPosition)
            }
        }

        return null
    }

    /**
     * Converts name value pairs to a map, with the last entry for each name being present.
     *
     * @param pairs Name value pairs to convert
     * @return The resulting map
     */
    fun convertToMapLayout(pairs: List<NameValuePair>): Map<String, String> {
        val map: MutableMap<String, String> = mutableMapOf()
        for (pair in pairs) {
            map[pair.name] = pair.value
        }

        return map
    }

    fun decodeUrlEncodedItems(input: String, escapedSeparator: Boolean): Map<String, String> {
        val kvPairs = URLEncodedUtils.parse(
            if (escapedSeparator) input.replace("""\\u0026""", "&") else input,
            Charsets.UTF_8
        )

        return convertToMapLayout(kvPairs)
    }

    fun arrayRangeEquals(array: ByteArray, offset: Int, segment: ByteArray): Boolean {
        if (array.size < offset + segment.size) {
            return false
        }

        return segment.withIndex().all { (it, part) -> part == array[it + offset] }
    }
}
