package com.sedmelluq.discord.lavaplayer.container

open class MediaContainerRegistry(val probes: List<MediaContainerProbe>) {
    companion object {
        @JvmField
        val DEFAULT_REGISTRY = MediaContainerRegistry(MediaContainer.asList())
    }

    constructor(vararg probes: MediaContainerProbe) : this(probes.toList())

    fun find(name: String): MediaContainerProbe? =
        probes.find { it.name == name }

    fun extend(vararg additional: MediaContainerProbe): MediaContainerRegistry =
        extend(additional.toSet())

    fun extend(additional: Iterable<MediaContainerProbe>): MediaContainerRegistry {
        val newProbes = probes
            .union(additional.toSet())
            .toList()

        return MediaContainerRegistry(newProbes)
    }
}
