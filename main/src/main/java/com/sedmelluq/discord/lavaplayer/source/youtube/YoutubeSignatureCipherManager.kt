package com.sedmelluq.discord.lavaplayer.source.youtube

import com.sedmelluq.discord.lavaplayer.source.youtube.format.YoutubeTrackFormat
import com.sedmelluq.discord.lavaplayer.tools.extensions.toRuntimeException
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import mu.KotlinLogging
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.utils.URIBuilder
import org.apache.http.util.EntityUtils
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Files
import java.util.regex.Pattern
import kotlin.io.path.writeText

/**
 * Handles parsing and caching of signature ciphers
 */
class YoutubeSignatureCipherManager : YoutubeSignatureResolver {
    companion object {
        private const val VARIABLE_PART = """[a-zA-Z_\$][a-zA-Z_0-9]*"""
        private const val VARIABLE_PART_DEFINE = """\"?$VARIABLE_PART\"?"""

        private const val BEFORE_ACCESS = """(?:\[\"|\.)"""
        private const val AFTER_ACCESS = """(?:\"\]|)"""

        private const val VARIABLE_PART_ACCESS = "$BEFORE_ACCESS$VARIABLE_PART$AFTER_ACCESS"
        private const val REVERSE_PART = """:function\(a\)\{(?:return )?a\.reverse\(\)\}"""
        private const val SLICE_PART = """:function\(a,b\)\{return a\.slice\(b\)\}"""
        private const val SPLICE_PART = """:function\(a,b\)\{a\.splice\(0,b\)\}"""
        private const val SWAP_PART = """:function\(a,b\)\{var c=a\[0\];a\[0\]=a\[b%a\.length\];a\[b(?:%a.length|)\]=c(?:;return a)?\}"""
        private const val PATTERN_PREFIX = """(?:^|,)\"?($VARIABLE_PART)\"?"""

        private val functionPattern = """function(?: $VARIABLE_PART)?\(a\)\{a=a\.split\(""\);\s*((?:(?:a=)?$VARIABLE_PART$VARIABLE_PART_ACCESS\(a,\d+\);)+)return a\.join\(""\)}""".toPattern()
        private val reversePattern = "$PATTERN_PREFIX$REVERSE_PART".toPattern(Pattern.MULTILINE)
        private val slicePattern = "$PATTERN_PREFIX$SLICE_PART".toPattern(Pattern.MULTILINE)
        private val splicePattern = "$PATTERN_PREFIX$SPLICE_PART".toPattern(Pattern.MULTILINE)
        private val swapPattern = "$PATTERN_PREFIX$SWAP_PART".toPattern(Pattern.MULTILINE)
        private val actionsPattern = """var ($VARIABLE_PART)=\{((?:(?:$VARIABLE_PART_DEFINE$REVERSE_PART|$VARIABLE_PART_DEFINE$SLICE_PART|$VARIABLE_PART_DEFINE$SPLICE_PART|$VARIABLE_PART_DEFINE$SWAP_PART),?\n?)+)};""".toPattern()
        private val signatureExtraction = """/s/([^/]+)/""".toPattern()
        private val timestampPattern = """(signatureTimestamp|sts)[\\:](\d+)""".toPattern()
        private val log = KotlinLogging.logger { }

        private fun extractDollarEscapedFirstGroup(pattern: Pattern, text: String): String? {
            val matcher = pattern.matcher(text)
            return if (matcher.find()) matcher.group(1).replace("$", "\\$") else null
        }

        private fun parseTokenScriptUrl(urlString: String): URI {
            try {
                val url = when {
                    urlString.startsWith("//") -> "https:$urlString"
                    urlString.startsWith("/") -> "https://www.youtube.com$urlString"
                    else -> urlString
                }

                return URI(url)
            } catch (e: URISyntaxException) {
                throw e.toRuntimeException()
            }
        }
    }

    private val cipherCache: MutableMap<String, YoutubeSignatureCipher> = mutableMapOf()
    private val dumpedScriptUrls: MutableSet<String> = hashSetOf()
    private val cipherLoadLock: Any = Any()

    /**
     * Produces a valid playback URL for the specified track
     *
     * @param httpInterface HTTP interface to use
     * @param playerScript  Address of the script which is used to decipher signatures
     * @param format        The track for which to get the URL
     * @return Valid playback URL
     * @throws IOException On network IO error
     */
    @Throws(IOException::class)
    override fun resolveFormatUrl(
        httpInterface: HttpInterface,
        playerScript: String?,
        format: YoutubeTrackFormat,
    ): URI {
        val signature = format.signature
            ?: return format.url

        val cipher = getCipherKeyAndTimestampFromScript(httpInterface, playerScript!!)
        return try {

            URIBuilder(format.url)
                .setParameter("ratebypass", "yes")
                .setParameter(format.signatureKey, cipher.apply(signature))
                .build()
        } catch (e: URISyntaxException) {
            throw RuntimeException(e)
        }
    }

    /**
     * Produces a valid dash XML URL from the possibly ciphered URL.
     *
     * @param httpInterface HTTP interface instance to use
     * @param playerScript  Address of the script which is used to decipher signatures
     * @param dashUrl       URL of the dash XML, possibly with a ciphered signature
     * @return Valid dash XML URL
     * @throws IOException On network IO error
     */
    @Throws(IOException::class)
    override fun resolveDashUrl(httpInterface: HttpInterface, playerScript: String?, dashUrl: String): String {
        val matcher = signatureExtraction.matcher(dashUrl)
        if (!matcher.find()) {
            return dashUrl
        }

        val cipher = getCipherKeyAndTimestampFromScript(httpInterface, playerScript!!)
        return matcher.replaceFirst("/signature/${cipher.apply(matcher.group(1))}/")
    }

    @Throws(IOException::class)
    override fun getCipherKeyAndTimestampFromScript(
        httpInterface: HttpInterface,
        playerScript: String,
    ): YoutubeSignatureCipher {
        var cipherKey = cipherCache[playerScript]
        if (cipherKey == null) {
            synchronized(cipherLoadLock) {
                log.debug { "Parsing cipher and timestamp from player script $playerScript" }

                httpInterface.execute(HttpGet(parseTokenScriptUrl(playerScript))).use { response ->
                    validateResponseCode(playerScript, response)

                    cipherKey = extractTokensAndTimestampFromScript(EntityUtils.toString(response.entity), playerScript)
                    cipherKey?.let { cipherCache[playerScript] = it }
                }
            }
        }

        return cipherKey!!
    }

    @Throws(IOException::class)
    private fun validateResponseCode(cipherScriptUrl: String, response: CloseableHttpResponse) {
        val statusCode = response.statusLine.statusCode
        if (!HttpClientTools.isSuccessWithContent(statusCode)) {
            throw IOException("Received non-success response code $statusCode from script url $cipherScriptUrl (${parseTokenScriptUrl(cipherScriptUrl)})")
        }
    }

    private fun getQuotedFunctions(vararg functionNames: String?): List<String> {
        return functionNames.filterNotNull().map { Pattern.quote(it) }
    }

    private fun dumpProblematicScript(script: String, sourceUrl: String, issue: String) {
        if (!dumpedScriptUrls.add(sourceUrl)) {
            return
        }

        try {
            val path = Files.createTempFile("lavaplayer-yt-player-script", ".js")
            path.writeText(script)
            log.error { "Problematic YouTube player script $sourceUrl detected (issue detected with script: $issue). Dumped to ${path.toAbsolutePath()}." }
        } catch (e: Exception) {
            log.error { "Failed to dump problematic YouTube player script $sourceUrl (issue detected with script: $issue)." }
        }
    }

    private fun extractTokensAndTimestampFromScript(script: String, sourceUrl: String): YoutubeSignatureCipher {
        val actions = actionsPattern.matcher(script)
        if (!actions.find()) {
            dumpProblematicScript(script, sourceUrl, "no actions match")
            throw IllegalStateException("Must find action functions from script: $sourceUrl")
        }

        val functions = functionPattern.matcher(script)
        if (!functions.find()) {
            dumpProblematicScript(script, sourceUrl, "no decipher function match")
            throw IllegalStateException("Must find decipher function from script.")
        }

        val scriptTimestamp = timestampPattern.matcher(script)
        if (!scriptTimestamp.find()) {
            dumpProblematicScript(script, sourceUrl, "no timestamp match")
            throw IllegalStateException("Must find timestamp from script: $sourceUrl")
        }

        val cipherKey = YoutubeSignatureCipher()
        cipherKey.scriptTimestamp = scriptTimestamp.group(2)

        val actionBody = actions.group(2)
        val reverseKey = extractDollarEscapedFirstGroup(reversePattern, actionBody)
        val slicePart = extractDollarEscapedFirstGroup(slicePattern, actionBody)
        val splicePart = extractDollarEscapedFirstGroup(splicePattern, actionBody)
        val swapKey = extractDollarEscapedFirstGroup(swapPattern, actionBody)
        val extractor = "(?:a=)?${Pattern.quote(actions.group(1))}$BEFORE_ACCESS(${getQuotedFunctions(reverseKey, slicePart, splicePart, swapKey).joinToString("|")})$AFTER_ACCESS\\(a,(\\d+)\\)".toPattern()
        val matcher = extractor.matcher(functions.group(1))

        while (matcher.find()) {
            when (matcher.group(1)) {
                swapKey ->
                    cipherKey.addOperation(YoutubeCipherOperation(YoutubeCipherOperationType.SWAP, matcher.group(2).toInt()))

                reverseKey ->
                    cipherKey.addOperation(YoutubeCipherOperation(YoutubeCipherOperationType.REVERSE, 0))

                slicePart ->
                    cipherKey.addOperation(YoutubeCipherOperation(YoutubeCipherOperationType.SLICE, matcher.group(2).toInt()))

                splicePart ->
                    cipherKey.addOperation(YoutubeCipherOperation(YoutubeCipherOperationType.SPLICE, matcher.group(2).toInt()))

                else -> dumpProblematicScript(script, sourceUrl, "unknown cipher operation found")
            }
        }

        if (cipherKey.isEmpty) {
            log.error("No operations detected from cipher extracted from {}.", sourceUrl)
            dumpProblematicScript(script, sourceUrl, "no cipher operations")
        }

        return cipherKey
    }
}
