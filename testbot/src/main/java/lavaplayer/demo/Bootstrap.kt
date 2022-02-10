package lavaplayer.demo

import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.cache.CacheFlag

object Bootstrap {
    @JvmStatic
    fun main(args: Array<out String>) {
        val token = checkNotNull(args.firstOrNull()) {
            "BOT_TOKEN variable must not be null"
        }

        JDABuilder.create(token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_VOICE_STATES)
            .addEventListeners(BotApplicationManager())
            .disableCache(CacheFlag.ACTIVITY, CacheFlag.EMOTE, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS)
            .build()
    }
}
