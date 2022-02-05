package com.sedmelluq.discord.lavaplayer.track.loader

import com.sedmelluq.discord.lavaplayer.source.common.SourceRegistry
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.lava.common.tools.DaemonThreadFactory
import com.sedmelluq.lava.common.tools.ExecutorTools
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

open class DefaultItemLoaderFactory(internal val sourceRegistry: SourceRegistry) : ItemLoaderFactory, CoroutineScope {
    companion object {
        private fun createThreadPool() = ThreadPoolExecutor(
            1,
            10,
            30L,
            TimeUnit.SECONDS,
            SynchronousQueue(false),
            DaemonThreadFactory("track-info")
        )
    }

    private val threadPool = createThreadPool()
    internal val dispatcher = threadPool.asCoroutineDispatcher()

    override val coroutineContext: CoroutineContext
        get() = dispatcher + SupervisorJob() + CoroutineName("Item Loader Factory")

    override var itemLoaderPoolSize: Int
        get() = threadPool.poolSize
        set(value) {
            threadPool.maximumPoolSize = value
        }

    override fun createItemLoader(reference: AudioReference): ItemLoader {
        return DefaultItemLoader(reference, this)
    }

    fun createItemLoader(reference: AudioReference, resultHandler: ItemLoadResultHandler? = null): ItemLoader {
        return DefaultItemLoader(reference, this, resultHandler)
    }

    override fun shutdown() {
        ExecutorTools.shutdownExecutor(threadPool, "track info")
    }
}
