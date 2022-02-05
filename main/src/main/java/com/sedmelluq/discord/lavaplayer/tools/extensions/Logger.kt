package com.sedmelluq.discord.lavaplayer.tools.extensions

import com.sedmelluq.lava.common.tools.exception.FriendlyException
import org.slf4j.Logger
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Log a FriendlyException appropriately according to its severity.
 *
 * @param exception   The exception itself
 * @param lazyMessage An object that is included in the log
 */
@OptIn(ExperimentalContracts::class)
inline fun Logger.friendlyError(exception: FriendlyException, lazyMessage: () -> Any) {
    contract {
        callsInPlace(lazyMessage, InvocationKind.EXACTLY_ONCE)
    }

    val context = lazyMessage().toString()
    when (exception.severity) {
        FriendlyException.Severity.COMMON ->
            debug("Common failure for $context: ${exception.message}")

        FriendlyException.Severity.SUSPICIOUS ->
            warn("Suspicious exception for $context", exception)

        else -> error("Error in $context", exception)
    }
}
