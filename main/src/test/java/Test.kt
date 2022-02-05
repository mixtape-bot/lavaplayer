import com.sedmelluq.discord.lavaplayer.manager.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.manager.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.common.SourceRegistry
import com.sedmelluq.discord.lavaplayer.tools.extensions.isSearchResult
import com.sedmelluq.discord.lavaplayer.track.loader.ItemLoadResult
import com.sedmelluq.lava.common.tools.io.MessageInput
import com.sedmelluq.lava.common.tools.io.MessageOutput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutionException

val playerManager: AudioPlayerManager = DefaultAudioPlayerManager()

suspend fun main() = coroutineScope {
    SourceRegistry.registerRemoteSources(playerManager)

    val query = "ytsearch:formula labrinth"
    launch { doQuery(query, true) }
    launch { doQuery(query, false) }
    Unit
}

suspend fun doQuery(query: String, long: Boolean) {
    try {
        when (val result = playerManager.items.createItemLoader(query).load()) {
            is ItemLoadResult.TrackLoaded -> println(
                """
                         ----------------------------------------
                              class:    ${result.track.javaClass.name}
                              title:    ${result.track.info.title}
                              author:   ${result.track.info.author}
                              uri:      ${result.track.info.uri}
                              duration: ${result.track.duration}
                              artwork:  ${result.track.info.artworkUrl}
                        """.trimIndent()
            )

            is ItemLoadResult.CollectionLoaded -> {
                println("long? $long")
                delay(if (long) 5000 else 500)

                println("search result? ${if (result.collection.isSearchResult) "yes" else "no"} long? $long")
                for (track in result.collection.tracks) {
                    val baos = ByteArrayOutputStream()
                    playerManager.encodeTrack(MessageOutput(baos), track)

                    val bais = ByteArrayInputStream(baos.toByteArray())
                    println(playerManager.decodeTrack(MessageInput(bais)))

                    println(
                        """
                         ----------------------------------------
                              title:    ${track.info.title}
                              author:   ${track.info.author}
                              uri:      ${track.info.uri}
                              duration: ${track.duration}
                              artwork:  ${track.info.artworkUrl}
                        """.trimIndent()
                    )
                }
            }

            is ItemLoadResult.NoMatches ->
                println("No matching items found")

            is ItemLoadResult.LoadFailed ->
                result.exception.printStackTrace()
        }
    } catch (e: InterruptedException) {
        e.printStackTrace()
    } catch (e: ExecutionException) {
        e.printStackTrace()
    }
}
