package lavaplayer.downloader

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats
import com.sedmelluq.discord.lavaplayer.manager.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.manager.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.common.SourceRegistry
import com.sedmelluq.discord.lavaplayer.tools.extensions.loadItemAsync
import com.sedmelluq.discord.lavaplayer.track.loader.ItemLoadResult
import org.slf4j.LoggerFactory

class Downloader(val ffmpegArgs: List<String> = emptyList()) {
    companion object {
        private val log = LoggerFactory.getLogger(Downloader::class.java)
        private val apm: AudioPlayerManager = DefaultAudioPlayerManager()

        init {
            apm.configuration.outputFormat = StandardAudioDataFormats.DISCORD_PCM_S16_BE

            SourceRegistry.registerRemoteSources(apm)
            SourceRegistry.registerLocalSources(apm)
        }
    }

    private var format: String = "wav"

    suspend fun start(query: String, format: String = "wav") {
        this@Downloader.format = format

        log.info("Starting downloader with query $query")
        val download = when (val item = apm.loadItemAsync(query).await()) {
            is ItemLoadResult.TrackLoaded -> {
                log.info("Loaded track: ${item.track}")
                Download(format, item.track, apm, ffmpegArgs)
            }

            is ItemLoadResult.CollectionLoaded -> {
                log.info("Loaded collection: ${item.collection.name}")
                Download(format, item.collection.tracks.first(), apm, ffmpegArgs)
            }

            is ItemLoadResult.LoadFailed -> return log.error("Unable to start Downloader", item.exception)
            is ItemLoadResult.NoMatches -> return log.info("Couldn't find anything for: $query")

            else -> return
        }

        download.start()
    }
}
