package com.sedmelluq.discord.lavaplayer.tools.extensions

inline fun <T> Result<T>.onComplete(block: () -> Unit): Result<T> {
    return this
        .onSuccess { block() }
        .onFailure { block() }
}
