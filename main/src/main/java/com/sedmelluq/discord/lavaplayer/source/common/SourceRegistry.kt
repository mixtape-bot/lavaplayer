package com.sedmelluq.discord.lavaplayer.source.common

import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry
import com.sedmelluq.discord.lavaplayer.source.ItemSourceManager
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampItemSourceManager
import com.sedmelluq.discord.lavaplayer.source.getyarn.GetyarnItemSourceManager
import com.sedmelluq.discord.lavaplayer.source.http.HttpItemSourceManager
import com.sedmelluq.discord.lavaplayer.source.local.LocalItemSourceManager
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudItemSourceManager
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamItemSourceManager
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoItemSourceManager
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeItemSourceManager

interface SourceRegistry {
    /**
     * Helpers for registering built-in source managers to a player manager.
     */
    companion object {
        /**
         * Registers all built-in remote audio sources to the specified player manager. Local file audio source must be
         * registered separately.
         *
         * @param sourceRegistry    Source registry to register the source managers to
         * @param containerRegistry Media container registry to be used by any probing sources.
         */
        @JvmOverloads
        @JvmStatic
        fun registerRemoteSources(
            sourceRegistry: SourceRegistry,
            containerRegistry: MediaContainerRegistry = MediaContainerRegistry.DEFAULT_REGISTRY
        ) {
            sourceRegistry.registerSourceManager(YoutubeItemSourceManager(true))
            sourceRegistry.registerSourceManager(SoundCloudItemSourceManager.createDefault())
            sourceRegistry.registerSourceManager(BandcampItemSourceManager())
            sourceRegistry.registerSourceManager(VimeoItemSourceManager())
            sourceRegistry.registerSourceManager(TwitchStreamItemSourceManager())
            sourceRegistry.registerSourceManager(GetyarnItemSourceManager())
            sourceRegistry.registerSourceManager(HttpItemSourceManager(containerRegistry))
        }

        /**
         * Registers the local file source manager to the specified player manager.
         *
         * @param sourceRegistry    Source registry to register the source manager to
         * @param containerRegistry Media container registry to be used by the local source.
         */
        @JvmOverloads
        @JvmStatic
        fun registerLocalSources(sourceRegistry: SourceRegistry, containerRegistry: MediaContainerRegistry = MediaContainerRegistry.DEFAULT_REGISTRY) {
            sourceRegistry.registerSourceManager(LocalItemSourceManager(containerRegistry))
        }
    }

    /**
     * The list of enabled source managers.
     */
    val sourceManagers: List<ItemSourceManager>

    /**
     * Registers an [ItemSourceManager]
     * @param sourceManager The source manager to register, which will be used for subsequent load item calls.
     */
    fun registerSourceManager(sourceManager: ItemSourceManager)

    /**
     * Shortcut for accessing a source manager of the specified class.
     *
     * @param klass The class of the source manager to return
     * @param T     The class of the source manager.
     *
     * @return The source manager of the specified class, or null if not registered.
     */
    fun <T : ItemSourceManager> source(klass: Class<T>): T?
}
