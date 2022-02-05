package com.sedmelluq.discord.lavaplayer.source.youtube.format.extractor

import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeSignatureResolver
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeTrackJsonData
import com.sedmelluq.discord.lavaplayer.source.youtube.format.YoutubeTrackFormat
import com.sedmelluq.discord.lavaplayer.source.youtube.format.YoutubeTrackFormatExtractor
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import mu.KotlinLogging
import org.apache.http.client.methods.HttpGet
import org.apache.http.entity.ContentType
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser

class LegacyDashMpdFormatsExtractor : YoutubeTrackFormatExtractor {
    companion object {
        private val log = KotlinLogging.logger {  }
    }

    override fun extract(response: YoutubeTrackJsonData, httpInterface: HttpInterface, signatureResolver: YoutubeSignatureResolver): List<YoutubeTrackFormat> {
        val dashUrl = response.polymerArguments["dashmpd"].text
            ?: return emptyList()

        return try {
            loadTrackFormatsFromDash(dashUrl, httpInterface, signatureResolver, response.playerScriptUrl)
        } catch (e: Exception) {
            throw RuntimeException("Failed to extract formats from dash url $dashUrl", e)
        }
    }

    private fun loadTrackFormatsFromDash(
        dashUrl: String,
        httpInterface: HttpInterface,
        signatureResolver: YoutubeSignatureResolver,
        playerScriptUrl: String?
    ): List<YoutubeTrackFormat> {
        val resolvedDashUrl = signatureResolver.resolveDashUrl(httpInterface, playerScriptUrl, dashUrl)

        httpInterface.execute(HttpGet(resolvedDashUrl)).use { response ->
            HttpClientTools.assertSuccessWithContent(response, "track info page response")

            val document = Jsoup.parse(response.entity.content, Charsets.UTF_8.name(), "", Parser.xmlParser())
            return loadTrackFormatsFromDashDocument(document)
        }
    }

    private fun loadTrackFormatsFromDashDocument(document: Document): List<YoutubeTrackFormat> {
        val tracks = mutableListOf<YoutubeTrackFormat>()
        for (adaptation in document.select("AdaptationSet")) {
            val mimeType = adaptation.attr("mimeType")
            for (representation in adaptation.select("Representation")) {
                val url = representation.select("BaseURL").first()!!.text()
                val contentLength = DataFormatTools.extractBetween(url, "/clen/", "/")
                val contentType = "$mimeType; codecs=${representation.attr("codecs")}"

                if (contentLength == null) {
                    log.debug { "Skipping format $contentType because the content length is missing" }
                    continue
                }

                val format = YoutubeTrackFormat(
                    url,
                    representation.attr("bandwidth").toLong(),
                    ContentType.parse(contentType),
                    contentLength.toLong(),
                    null,
                    YoutubeTrackFormatExtractor.DEFAULT_SIGNATURE_KEY
                )

                tracks.add(format)
            }
        }

        return tracks
    }
}
