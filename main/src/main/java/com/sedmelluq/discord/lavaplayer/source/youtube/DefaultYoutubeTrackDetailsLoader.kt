package com.sedmelluq.discord.lavaplayer.source.youtube

import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeTrackJsonData.Companion.fromMainResult
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools
import com.sedmelluq.discord.lavaplayer.tools.extensions.toRuntimeException
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import com.sedmelluq.discord.lavaplayer.tools.json.JsonBrowser
import com.sedmelluq.lava.common.tools.exception.FriendlyException
import com.sedmelluq.lava.common.tools.exception.friendlyError
import mu.KotlinLogging
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.util.EntityUtils
import java.io.IOException

open class DefaultYoutubeTrackDetailsLoader : YoutubeTrackDetailsLoader {
    companion object {
        private val log = KotlinLogging.logger {  }
    }

    @Volatile
    private var cachedPlayerScript: CachedPlayerScript? = null

    override fun loadDetails(
        httpInterface: HttpInterface,
        videoId: String,
        requireFormats: Boolean,
        sourceManager: YoutubeItemSourceManager,
    ): YoutubeTrackDetails? {
        return try {
            load(httpInterface, videoId, requireFormats, sourceManager)
        } catch (e: IOException) {
            throw e.toRuntimeException()
        }
    }

    @Throws(IOException::class)
    private fun load(
        httpInterface: HttpInterface,
        videoId: String,
        requireFormats: Boolean,
        sourceManager: YoutubeItemSourceManager,
    ): YoutubeTrackDetails? {
        val mainInfo = loadTrackInfoFromInnertube(httpInterface, videoId, sourceManager, null)
        return try {
            val initialData = loadBaseResponse(mainInfo, httpInterface, videoId, sourceManager)
                ?: return null

            DefaultYoutubeTrackDetails(
                videoId = videoId,
                data = augmentWithPlayerScript(initialData, httpInterface, videoId, requireFormats)
            )
        } catch (e: FriendlyException) {
            throw e
        } catch (e: Exception) {
            throw ExceptionTools.throwWithDebugInfo(log, "Error when extracting data", "mainJson", mainInfo.format(), e)
        }
    }

    @Throws(IOException::class)
    protected fun loadBaseResponse(
        mainInfo: JsonBrowser,
        httpInterface: HttpInterface,
        videoId: String,
        sourceManager: YoutubeItemSourceManager,
    ): YoutubeTrackJsonData? {
        val data = fromMainResult(mainInfo)

        val status = checkPlayabilityStatus(data.playerResponse)
        return when {
            status == InfoStatus.DOES_NOT_EXIST ->
                null

            status == InfoStatus.CONTENT_CHECK_REQUIRED ->
                fromMainResult(loadTrackInfoWithContentVerify(httpInterface, videoId))

            status == InfoStatus.NON_EMBEDDABLE -> {
                val trackInfo = loadTrackInfoFromInnertube(httpInterface, videoId, sourceManager, status)
                checkPlayabilityStatus(trackInfo)
                return fromMainResult(trackInfo)
            }

//            status == InfoStatus.REQUIRES_LOGIN && requireFormats ->
//                fromMainResult(loadTrackInfoFromInnertube(httpInterface, videoId, sourceManager))

            else -> data
        }
    }

    protected fun checkPlayabilityStatus(playerResponse: JsonBrowser): InfoStatus {
        val statusBlock = playerResponse["playabilityStatus"]
        if (statusBlock.isNull) {
            throw RuntimeException("No playability status block.")
        }

        when (statusBlock["status"].text) {
            null ->
                throw RuntimeException("No playability status field.")

            "ERROR" -> {
                val reason = statusBlock["reason"].text
                return if ("This video is unavailable" == reason) {
                    InfoStatus.DOES_NOT_EXIST
                } else {
                    friendlyError(reason)
                }
            }

            "LOGIN_REQUIRED" -> {
                val errorReason = statusBlock["errorScreen"]["playerErrorMessageRenderer"]["reason"]["simpleText"].text
                if ("This video is private" == errorReason) {
                    friendlyError("This is a private video.")
                }

                if ("This video may be inappropriate for some users." == errorReason) {
                    friendlyError("This video requires age verification", FriendlyException.Severity.SUSPICIOUS)
                }

                return InfoStatus.REQUIRES_LOGIN
            }

            "UNPLAYABLE" -> {
                val reason = getUnplayableReason(statusBlock)
                if (reason == "Playback on other websites has been disabled by the video owner.") {
                    return InfoStatus.NON_EMBEDDABLE
                }

                friendlyError(reason)
            }

            "OK" ->
                return InfoStatus.INFO_PRESENT

            "CONTENT_CHECK_REQUIRED" ->
                return InfoStatus.CONTENT_CHECK_REQUIRED

            else -> friendlyError("This video cannot be viewed anonymously.")
        }

    }

    protected fun getUnplayableReason(statusBlock: JsonBrowser): String? {
        var unplayableReason = statusBlock["reason"].text

        /* check for sub reason */
        val playerErrorMessage = statusBlock["errorScreen"]["playerErrorMessageRenderer"]
        if (!playerErrorMessage["subreason"].isNull) {
            val subReason = playerErrorMessage["subreason"]
            if (!subReason["simpleText"].isNull) {
                unplayableReason = subReason["simpleText"].text
            } else if (!subReason["runs"].isNull && subReason["runs"].isList) {
                unplayableReason = subReason["runs"]
                    .values()
                    .joinToString("\n") { it["text"].safeText }
            }
        }

        return unplayableReason
    }

    @Throws(IOException::class)
    protected fun loadTrackInfoFromInnertube(
        httpInterface: HttpInterface,
        videoId: String,
        sourceManager: YoutubeItemSourceManager,
        status: InfoStatus? = null,
    ): JsonBrowser {
        if (cachedPlayerScript == null) {
            fetchScript(videoId, httpInterface)
        }

        val playerScriptTimestamp = sourceManager.signatureResolver.getCipherKeyAndTimestampFromScript(
            httpInterface = httpInterface,
            playerScript = cachedPlayerScript!!.playerScriptUrl
        )

        val post = HttpPost(YoutubeConstants.PLAYER_URL)
        if (status == InfoStatus.NON_EMBEDDABLE) {
            post.entity = StringEntity(
                YoutubeConstants.PLAYER_PAYLOAD.format(videoId, playerScriptTimestamp.scriptTimestamp),
                Charsets.UTF_8
            )
        } else {
            post.entity = StringEntity(
                YoutubeConstants.PLAYER_EMBED_PAYLOAD.format(videoId, playerScriptTimestamp.scriptTimestamp),
                Charsets.UTF_8
            )
        }

        httpInterface.execute(post).use { response ->
            return processResponse(response)
        }
    }

    @Throws(IOException::class)
    protected fun loadTrackInfoFromMainPage(httpInterface: HttpInterface, videoId: String): JsonBrowser {
        val url = "https://www.youtube.com/watch?v=$videoId&pbj=1&hl=en"
        httpInterface.execute(HttpGet(url)).use { response -> return processResponse(response) }
    }

    @Throws(IOException::class)
    protected fun loadTrackInfoWithContentVerify(httpInterface: HttpInterface, videoId: String): JsonBrowser {
        val post = HttpPost(YoutubeConstants.VERIFY_AGE_URL)
        post.entity = StringEntity(
            YoutubeConstants.VERIFY_AGE_PAYLOAD.format("/watch?v=$videoId"),
            Charsets.UTF_8
        )

        httpInterface.execute(post).use { response ->
            HttpClientTools.assertSuccessWithContent(response, "content verify response")

            val json = JsonBrowser.parse(response.entity.content)
            val fetchedContentVerifiedLink = json["actions"][0]["navigateAction"]["endpoint"]["urlEndpoint"]["url"].text
            if (fetchedContentVerifiedLink != null) {
                return loadTrackInfoFromMainPage(httpInterface, fetchedContentVerifiedLink.substring(9))
            }

            log.error { "Did not receive requested content verified link on track $videoId response: ${json.format()}" }
        }

        friendlyError("Track requires content verification.", FriendlyException.Severity.SUSPICIOUS, IllegalStateException("Expected response is not present."))
    }

    @Throws(IOException::class)
    protected fun processResponse(response: CloseableHttpResponse): JsonBrowser {
        HttpClientTools.assertSuccessWithContent(response, "video page response")

        return try {
            JsonBrowser.parse(response.entity.content)
        } catch (e: FriendlyException) {
            throw e
        } catch (e: Exception) {
            val responseText = EntityUtils.toString(response.entity, Charsets.UTF_8)
            friendlyError("Received unexpected response from YouTube.", FriendlyException.Severity.SUSPICIOUS, RuntimeException("Failed to parse: $responseText", e))
        }
    }

    @Throws(IOException::class)
    protected fun augmentWithPlayerScript(
        data: YoutubeTrackJsonData,
        httpInterface: HttpInterface,
        videoId: String,
        requireFormats: Boolean,
    ): YoutubeTrackJsonData {
        val now = System.currentTimeMillis()
        if (data.playerScriptUrl != null) {
            cachedPlayerScript = CachedPlayerScript(data.playerScriptUrl, now)
            return data
        } else if (!requireFormats) {
            return data
        }

        val cached = cachedPlayerScript
        return if (cached != null && (cached.timestamp + 600000L) >= now) {
            data.withPlayerScriptUrl(cached.playerScriptUrl)
        } else {
            data.withPlayerScriptUrl(fetchScript(videoId, httpInterface))
        }
    }

    @Throws(IOException::class)
    private fun fetchScript(videoId: String, httpInterface: HttpInterface): String {
        val now = System.currentTimeMillis()

        httpInterface.execute(HttpGet("https://www.youtube.com/embed/$videoId")).use { response ->
            HttpClientTools.assertSuccessWithContent(response, "youtube embed video id")

            val responseText: String = EntityUtils.toString(response.entity, Charsets.UTF_8)
            val encodedUrl = DataFormatTools.extractBetween(responseText, """"jsUrl":"""", """"""")
                ?: throw ExceptionTools.throwWithDebugInfo(
                    log = log,
                    message = "no jsUrl found",
                    name = "html",
                    value = responseText
                )

            cachedPlayerScript = CachedPlayerScript(encodedUrl, now)
            return encodedUrl
        }
    }

    enum class InfoStatus {
        INFO_PRESENT, REQUIRES_LOGIN, DOES_NOT_EXIST, CONTENT_CHECK_REQUIRED, NON_EMBEDDABLE
    }

    data class CachedPlayerScript(val playerScriptUrl: String, val timestamp: Long)
}
