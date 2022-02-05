package com.sedmelluq.discord.lavaplayer.natives

import com.sedmelluq.lava.common.natives.NativeLibraryLoader
import com.sedmelluq.lava.common.natives.architecture.DefaultOperatingSystemTypes

/**
 * Methods for loading the connector library.
 */
object ConnectorNativeLibLoader {
    private val loaders = listOf(
        NativeLibraryLoader.createFiltered("libmpg123-0", ConnectorNativeLibLoader::class.java) {
            it.osType === DefaultOperatingSystemTypes.WINDOWS
        },
        NativeLibraryLoader.create("connector", ConnectorNativeLibLoader::class.java)
    )

    /**
     * Loads the connector library with its dependencies for the current system
     */
    fun loadConnectorLibrary() {
        for (loader in loaders) {
            loader.load()
        }
    }
}
