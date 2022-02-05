package com.sedmelluq.discord.lavaplayer.track.loader.message

import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlin.reflect.KClass

interface ItemLoaderMessages {
    fun <T : ItemLoaderMessage> on(clazz: KClass<T>, block: suspend T.() -> Unit): Job
    fun send(message: ItemLoaderMessage): Boolean
    fun shutdown()

    companion object None : ItemLoaderMessages {
        override fun send(message: ItemLoaderMessage): Boolean = false

        override fun <T : ItemLoaderMessage> on(clazz: KClass<T>, block: suspend T.() -> Unit): Job = NonCancellable

        override fun shutdown() = Unit
    }
}
