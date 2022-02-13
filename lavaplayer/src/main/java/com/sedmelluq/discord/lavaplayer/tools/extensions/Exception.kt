package com.sedmelluq.discord.lavaplayer.tools.extensions

fun Exception.toRuntimeException(): RuntimeException =
    if (this is RuntimeException) this else RuntimeException(this)

/**
 * Makes sure thread is set to interrupted state when this throwable is an InterruptedException
 */
fun Throwable.keepInterrupted() {
    if (this is InterruptedException) Thread.currentThread().interrupt()
}

/**
 * Sometimes it is necessary to catch Throwable instances for logging or reporting purposes. However, unless for
 * specific known cases, Error instances should not be blocked from propagating, so rethrow them.
 */
fun Throwable.rethrow() {
    if (this is Error) throw this
}
