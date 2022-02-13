package com.sedmelluq.discord.lavaplayer.source.common

import com.sedmelluq.discord.lavaplayer.track.AudioItem

fun interface ExtractorRouter<R : LinkRoutes> {
    /**
     * @param routes The [LinkRoutes] to use
     * @param context The context
     */
    suspend fun extract(routes: R, context: ExtractorContext): AudioItem?
}
