package com.sedmelluq.lava.extensions.iprotator

import com.sedmelluq.lava.extensions.iprotator.planner.AbstractRoutePlanner
import com.sedmelluq.discord.lavaplayer.source.common.SourceRegistry
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeHttpContextFilter
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeItemSourceManager
import com.sedmelluq.discord.lavaplayer.tools.extensions.source
import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpConfigurable
import com.sedmelluq.discord.lavaplayer.tools.http.HttpContextFilter

public class YoutubeIpRotatorSetup(routePlanner: AbstractRoutePlanner) : IpRotatorSetup(routePlanner) {
    public companion object {
        private const val DEFAULT_RETRY_LIMIT = 4
        private val DEFAULT_DELEGATE: HttpContextFilter = YoutubeHttpContextFilter()
        private val RETRY_HANDLER = IpRotatorRetryHandler()

        public operator fun invoke(routePlanner: AbstractRoutePlanner, build: YoutubeIpRotatorSetup.() -> Unit): YoutubeIpRotatorSetup {
            return YoutubeIpRotatorSetup(routePlanner)
                .apply(build)
        }
    }

    private val mainConfiguration = mutableListOf<ExtendedHttpConfigurable>()
    private val searchConfiguration = mutableListOf<ExtendedHttpConfigurable>()

    public var retryLimit: Int = DEFAULT_RETRY_LIMIT
    public var mainDelegate: HttpContextFilter = DEFAULT_DELEGATE
    public var searchDelegate: HttpContextFilter? = null

    override val retryHandler: IpRotatorRetryHandler = RETRY_HANDLER

    /**
     * Applies this ip-rotator configuration to the supplied [sourceManager]
     *
     * @param sourceManager The [YoutubeItemSourceManager] to apply to.
     */
    public fun applyTo(sourceManager: YoutubeItemSourceManager): YoutubeIpRotatorSetup {
        useConfiguration(sourceManager.mainHttpConfiguration, false)
        useConfiguration(sourceManager.searchHttpConfiguration, true)
        useConfiguration(sourceManager.searchMusicHttpConfiguration, true)
        return this
    }

    /**
     * Applies this ip-rotator configuration to the supplied [registry]
     *
     * @param registry The [SourceRegistry] to apply to.
     */
    public fun applyTo(registry: SourceRegistry): YoutubeIpRotatorSetup {
        val sourceManager = registry.source<YoutubeItemSourceManager>()
        sourceManager?.let { applyTo(it) }
        return this
    }

    public fun withRetryLimit(limit: Int): YoutubeIpRotatorSetup {
        retryLimit = limit
        return this
    }

    public fun withMainDelegateFilter(filter: HttpContextFilter): YoutubeIpRotatorSetup {
        mainDelegate = filter
        return this
    }

    public fun withSearchDelegateFilter(filter: HttpContextFilter?): YoutubeIpRotatorSetup {
        searchDelegate = filter
        return this
    }

    override fun setup() {
        apply(mainConfiguration, IpRotatorFilter(mainDelegate, false, routePlanner, retryLimit))
        apply(searchConfiguration, IpRotatorFilter(searchDelegate, true, routePlanner, retryLimit))
    }

    private fun useConfiguration(configurable: ExtendedHttpConfigurable, isSearch: Boolean): YoutubeIpRotatorSetup {
        val configurations = if (isSearch) searchConfiguration else mainConfiguration
        configurations.add(configurable)
        return this
    }
}
