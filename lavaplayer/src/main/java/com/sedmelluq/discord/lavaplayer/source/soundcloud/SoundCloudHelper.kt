package com.sedmelluq.discord.lavaplayer.source.soundcloud

import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream
import com.sedmelluq.discord.lavaplayer.tools.json.JsonBrowser
import java.io.IOException
import java.net.URI

object SoundCloudHelper {
    @JvmStatic
    fun nonMobileUrl(url: String): String =
        url.replace("""^https?://m\.""".toRegex(), "https://")

    @Throws(IOException::class)
    @JvmStatic
    fun loadPlaybackUrl(httpInterface: HttpInterface, jsonUrl: String): String {
        PersistentHttpStream(httpInterface, URI.create(jsonUrl), null).use { stream ->
            if (!HttpClientTools.isSuccessWithContent(stream.checkStatusCode())) {
                throw IOException("Invalid status code for soundcloud stream: ${stream.checkStatusCode()}")
            }

            return JsonBrowser.parse(stream)["url"].safeText
        }
    }
}
