package com.sedmelluq.discord.lavaplayer.source.soundcloud

import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager
import mu.KotlinLogging
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.util.EntityUtils
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.math.max

class SoundCloudClientIdTracker(private val httpInterfaceManager: HttpInterfaceManager) {
    companion object {
        private const val ID_FETCH_CONTEXT_ATTRIBUTE = "sc-raw"
        private const val PAGE_APP_SCRIPT_REGEX = "https://[A-Za-z0-9-.]+/assets/[a-f0-9-]+\\.js"
        private const val APP_SCRIPT_CLIENT_ID_REGEX = "[^_]client_id:\"([a-zA-Z0-9-_]+)\""
        private const val EXPECTED_CLIENT_SCRIPT_INDEX = 8

        private val CLIENT_ID_REFRESH_INTERVAL = TimeUnit.HOURS.toMillis(1)
        private val pageAppScriptPattern = Pattern.compile(PAGE_APP_SCRIPT_REGEX)
        private val appScriptClientIdPattern = Pattern.compile(APP_SCRIPT_CLIENT_ID_REGEX)
        private val log = KotlinLogging.logger { }
    }

    private val clientIdLock = Any()
    private var clientId: String? = null
    private var lastClientIdUpdate: Long = 0
    private var lastClientScriptIndex = EXPECTED_CLIENT_SCRIPT_INDEX

    /**
     * Updates the clientID if more than [.CLIENT_ID_REFRESH_INTERVAL] time has passed since last updated.
     */
    fun updateClientId() {
        synchronized(clientIdLock) {
            val now = System.currentTimeMillis()
            if (now - lastClientIdUpdate < CLIENT_ID_REFRESH_INTERVAL) {
                log.debug { "Client ID was recently updated, not updating again right away." }
                return
            }

            lastClientIdUpdate = now
            log.info { ("Updating SoundCloud client ID (current is $clientId).") }
            try {
                clientId = findClientIdFromSite()
                log.info { "Updating SoundCloud client ID succeeded, new ID is $clientId." }
            } catch (e: Exception) {
                log.error(e) { "SoundCloud client ID update failed." }
            }
        }
    }

    fun ensureClientId(): String? {
        synchronized(clientIdLock) {
            if (clientId == null) {
                updateClientId()
            }

            return clientId
        }
    }

    fun isIdFetchContext(context: HttpClientContext): Boolean =
        context.getAttribute(ID_FETCH_CONTEXT_ATTRIBUTE) == true

    @Throws(IOException::class)
    private fun findClientIdFromSite(): String {
        httpInterfaceManager.get().use { httpInterface ->
            httpInterface.context.setAttribute(ID_FETCH_CONTEXT_ATTRIBUTE, true)
            val scriptUrls = findScriptUrls(httpInterface)
            return findClientIdFromScripts(httpInterface, scriptUrls)
        }
    }

    @Throws(IOException::class)
    private fun findScriptUrls(httpInterface: HttpInterface): List<String> {
        httpInterface.execute(HttpGet("https://soundcloud.com/discover")).use { response ->
            HttpClientTools.assertSuccessWithContent(response, "main page response")

            val page: String = EntityUtils.toString(response.entity, Charsets.UTF_8)
            val matcher = pageAppScriptPattern.matcher(page)
            val scriptUrls = mutableListOf<String>()
            while (matcher.find()) {
                scriptUrls.add(matcher.group())
            }

            return scriptUrls
        }
    }

    @Throws(IOException::class)
    private fun findClientIdFromScripts(httpInterface: HttpInterface, scriptUrls: List<String>): String {
        for (index in getIndicesByDistance(lastClientScriptIndex, scriptUrls.size)) {
            val url = scriptUrls[index]
            val clientId = findClientIdFromApplicationScript(httpInterface, url)
            if (clientId != null) {
                if (index != lastClientScriptIndex) {
                    log.info { "Last known client script index changed to $index, should update default for efficiency." }
                    lastClientScriptIndex = index
                }

                return clientId
            }
        }

        error("Could not find client ID from ${scriptUrls.size} script candidates.")
    }

    @Throws(IOException::class)
    private fun findClientIdFromApplicationScript(httpInterface: HttpInterface, scriptUrl: String): String? {
        httpInterface.execute(HttpGet(scriptUrl)).use { response ->
            HttpClientTools.assertSuccessWithContent(response, "application script response")

            val page: String = EntityUtils.toString(response.entity, Charsets.UTF_8)
            val clientIdMatcher = appScriptClientIdPattern.matcher(page)

            return if (clientIdMatcher.find()) clientIdMatcher.group(1) else null
        }
    }

    // Returns range [0, size) ordered by distance from center
    private fun getIndicesByDistance(center: Int, size: Int): IntArray {
        val maximumOffset = max(size, center)
        val indices = IntArray(size)
        var indicesFilled = 0
        for (offset in 0 until maximumOffset) {
            val forwardIndex = center + offset
            if (forwardIndex in 0 until size) {
                indices[indicesFilled++] = forwardIndex
            }

            if (offset > 0) {
                val backwardsIndex = center - offset
                if (backwardsIndex in 0 until size) {
                    indices[indicesFilled++] = backwardsIndex
                }
            }
        }

        return indices
    }
}
