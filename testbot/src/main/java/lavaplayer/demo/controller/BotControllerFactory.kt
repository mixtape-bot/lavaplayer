package lavaplayer.demo.controller

import lavaplayer.demo.BotApplicationManager
import lavaplayer.demo.BotGuildContext
import net.dv8tion.jda.api.entities.Guild

interface BotControllerFactory<T : BotController?> {
    val controllerClass: Class<T>

    fun create(manager: BotApplicationManager, state: BotGuildContext, guild: Guild): T
}
