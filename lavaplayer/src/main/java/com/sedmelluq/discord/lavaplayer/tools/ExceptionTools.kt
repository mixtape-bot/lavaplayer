package com.sedmelluq.discord.lavaplayer.tools

import com.sedmelluq.discord.lavaplayer.tools.extensions.into
import com.sedmelluq.discord.lavaplayer.tools.extensions.toRuntimeException
import mu.KotlinLogging
import org.slf4j.Logger
import java.util.*

/**
 * Contains common helper methods for dealing with exceptions.
 */
object ExceptionTools {
    private val log = KotlinLogging.logger {  }

    @Volatile
    private var debugInfoHandler: ErrorDebugInfoHandler = DefaultErrorDebugInfoHandler()

    @JvmStatic
    fun toRuntimeException(e: Exception): RuntimeException = e.toRuntimeException()

    /**
     * Finds the first exception which is an instance of the specified class from the throwable cause chain.
     *
     * @param throwable Throwable to scan.
     * @param klass     The throwable class to scan for.
     * @param <T>       The throwable class to scan for.
     * @return The first exception in the cause chain (including itself) which is an instance of the specified class.
     */
    fun <T : Throwable?> findDeepException(throwable: Throwable?, klass: Class<T>): T? {
        var t = throwable
        while (t != null) {
            if (klass.isAssignableFrom(t.javaClass)) {
                return t as? T
            }

            t = t.cause
        }

        return null
    }

    fun setDebugInfoHandler(debugInfoHandler: ErrorDebugInfoHandler) {
        ExceptionTools.debugInfoHandler = debugInfoHandler
    }

    @JvmStatic
    fun throwWithDebugInfo(
        log: Logger?,
        message: String,
        name: String,
        value: String?,
        cause: Throwable? = null
    ): RuntimeException {
        val debugInfo = ErrorDebugInfo(log, UUID.randomUUID().toString(), cause, message, name, value)
        debugInfoHandler.handle(debugInfo)

        return RuntimeException("$message EID: ${debugInfo.errorId}, $name <redacted>", cause)
    }

    fun interface ErrorDebugInfoHandler {
        fun handle(payload: ErrorDebugInfo)
    }

    data class ErrorDebugInfo(
        val log: Logger?,
        val errorId: String,
        val cause: Throwable?,
        val message: String,
        val name: String,
        val value: String?
    )

    class DefaultErrorDebugInfoHandler : ErrorDebugInfoHandler {
        override fun handle(payload: ErrorDebugInfo) =
            log.warn { "${payload.message} EID: ${payload.errorId}, ${payload.name}: ${payload.value}" }
    }
}
