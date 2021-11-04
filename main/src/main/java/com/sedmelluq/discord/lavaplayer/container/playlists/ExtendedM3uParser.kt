package com.sedmelluq.discord.lavaplayer.container.playlists

import java.util.regex.Pattern

/**
 * Parser for extended M3U lines, handles the format where directives have named arguments, for example:
 * #SOMETHING:FOO="thing",BAR=4
 */
object ExtendedM3uParser {
    private val directiveArgumentPattern = Pattern.compile("([A-Z-]+)=(?:\"([^\"]*)\"|([^,]*))(?:,|\\z)")

    /**
     * Parses one line.
     *
     * @param line Line.
     * @return Line object describing the directive or data on the line.
     */
    @JvmStatic
    fun parseLine(line: String): Line {
        val trimmed = line.trim { it <= ' ' }
        return when {
            trimmed.isEmpty() -> Line.EMPTY_LINE
            !trimmed.startsWith("#") -> Line(trimmed, null, emptyMap(), null)
            else -> parseDirectiveLine(trimmed)
        }
    }

    private fun parseDirectiveLine(line: String): Line {
        val parts = line.split(":".toRegex(), 2).toTypedArray()
        if (parts.size == 1) {
            return Line(null, line.substring(1), emptyMap(), "")
        }

        val matcher = directiveArgumentPattern.matcher(parts[1])
        val arguments: MutableMap<String, String> = HashMap()
        while (matcher.find()) {
            arguments[matcher.group(1)] = matcher.group(2) ?: matcher.group(3)
        }

        return Line(null, parts[0].substring(1), arguments, parts[1])
    }

    /**
     * Parsed extended M3U line info. May be either an empty line (isDirective() and isData() both false), a directive
     * or a data line.
     */
    class Line internal constructor(
        /**
         * The data of a data line.
         */
        @JvmField val lineData: String?,
        /**
         * Directive name of a directive line.
         */
        @JvmField val directiveName: String?,
        /**
         * Directive arguments of a directive line.
         */
        @JvmField val directiveArguments: Map<String, String>?,
        /**
         * Raw unprocessed directive extra data (where arguments are parsed from).
         */
        @JvmField val extraData: String?
    ) {
        companion object {
            internal val EMPTY_LINE = Line(null, null, null, null)
        }

        /**
         * @return True if it is a directive line.
         */
        val isDirective: Boolean
            get() = directiveName != null

        /**
         * @return True if it is a data line.
         */
        val isData: Boolean
            get() = lineData != null
    }
}
