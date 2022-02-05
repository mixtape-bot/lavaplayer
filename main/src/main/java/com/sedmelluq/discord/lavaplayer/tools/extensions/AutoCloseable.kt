package com.sedmelluq.discord.lavaplayer.tools.extensions

import mu.KotlinLogging

internal val closeWithWarningsLog = KotlinLogging.logger { }

/**
 * Closes the specified closeable object. In case that throws an error, logs the error with WARN level, but does not
 * rethrow.
 */
fun AutoCloseable.closeWithWarnings() {
    try {
        close()
    } catch (e: Exception) {
        closeWithWarningsLog.warn(e) { "Failed to close." }
    }
}
