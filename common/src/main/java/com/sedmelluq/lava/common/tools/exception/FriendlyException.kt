package com.sedmelluq.lava.common.tools.exception

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * An exception with a friendly message.
 *
 * @param friendlyMessage A message which is understandable to end-users
 * @param severity        Severity of the exception
 * @param cause           The cause of the exception with technical details
 */
public class FriendlyException(
    friendlyMessage: String?,
    /**
     * Severity of the exception
     */
    @JvmField public val severity: Severity,
    cause: Throwable?
) : RuntimeException(friendlyMessage, cause) {
    init {
        addSuppressed(EnvironmentInformation.INSTANCE)
    }

    /**
     * Severity levels for FriendlyException
     */
    public enum class Severity {
        /**
         * The cause is known and expected, indicates that there is nothing wrong with the library itself.
         */
        COMMON,

        /**
         * The cause might not be exactly known, but is possibly caused by outside factors. For example when an outside
         * service responds in a format that we do not expect.
         */
        SUSPICIOUS,

        /**
         * If the probable cause is an issue with the library or when there is no way to tell what the cause might be.
         * This is the default level and other levels are used in cases where the thrower has more in-depth knowledge
         * about the error.
         */
        FAULT;

        public companion object {
            public operator fun get(ord: Int): Severity = values()[ord]
        }
    }
}

public fun friendlyError(message: String? = null, severity: FriendlyException.Severity = FriendlyException.Severity.COMMON, cause: Throwable? = null): Nothing {
    throw FriendlyException(message, severity, cause)
}

@OptIn(ExperimentalContracts::class)
public inline fun friendlyCheck(value: Boolean, severity: FriendlyException.Severity = FriendlyException.Severity.COMMON, lazyMessage: () -> Any) {
    contract {
        returns() implies value
    }

    if (!value) {
        val message = lazyMessage()
        throw FriendlyException("Check has failed", severity, IllegalStateException(message.toString()))
    }
}

/**
 * If this exception is not a FriendlyException, wrap with a FriendlyException with the given message
 *
 * @param message   Message of the new FriendlyException if needed
 * @param severity  Severity of the new FriendlyException

 * @return `this` or wrapped exception
 */
public fun Throwable.wrapUnfriendlyException(message: String, severity: FriendlyException.Severity = FriendlyException.Severity.COMMON): FriendlyException {
    return if (this is FriendlyException) this else FriendlyException(message, severity, this)
}

/**
 * If this exception is not a FriendlyException, wrap with a RuntimeException
 *
 * @return Original or wrapped exception
 */
public fun Throwable.wrapUnfriendlyException(): RuntimeException {
    return if (this is FriendlyException) this else RuntimeException(this)
}
