package lavaplayer.demo

import com.soywiz.korau.sound.readMusic
import com.soywiz.korio.file.std.applicationVfs
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

object Bootstrap {
    @JvmStatic
    fun main(args: Array<out String>) {
        val token = checkNotNull(args.firstOrNull() ?: System.getenv("BOT_TOKEN")) {
            "BOT_TOKEN variable must not be null"
        }


        JDABuilder.create(token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_VOICE_STATES)
            .addEventListeners(BotApplicationManager())
            .disableCache(CacheFlag.ACTIVITY, CacheFlag.EMOTE, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS)
            .build()
    }
}

@OptIn(ExperimentalTime::class)
suspend fun main() {
    val (data, took)  = measureTimedValue {
        applicationVfs["dev/riddle.mp3"]
            .readMusic()
            .decode()
    }

    println(took.toString())
    println(data)
//    data.playAndWait()
}
