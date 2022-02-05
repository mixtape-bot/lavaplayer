package com.sedmelluq.discord.lavaplayer.tools.extensions

import com.sedmelluq.discord.lavaplayer.tools.Units

val linkPattern = "^https?://.+".toPattern()

fun String.isLink() =
    linkPattern.matcher(this).find()

/**
 * Parses this string into milliseconds
 *
 * @return The duration in milliseconds.
 */
fun String.parseDuration(): Long {
    val parts = split(":".toRegex()).mapNotNull { it.toIntOrNull() }
    return when (parts.size) {
        3 -> {
            val (hours, minutes, seconds) = parts
            hours * 3600000L + minutes * 60000L + seconds * 1000L
        }

        2 -> {
            val (minutes, seconds) = parts
            minutes * 60000L + seconds * 1000L
        }

        else -> Units.DURATION_MS_UNKNOWN
    }
}

/**
 * Converts this string if in the format HH:mm:ss (or mm:ss or ss) to milliseconds. Does not support day count.
 *
 * @return Duration in milliseconds.
 */
fun String.parseMilliseconds(): Long {
    var length = 0
    for (part in split("[:.]".toRegex())) {
        length = length * 60 + part.toInt()
    }

    return length * 1000L
}
