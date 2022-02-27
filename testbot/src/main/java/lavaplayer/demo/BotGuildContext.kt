package lavaplayer.demo

import lavaplayer.demo.controller.BotController

data class BotGuildContext(val guildId: Long) {
    @JvmField
    val controllers: MutableMap<Class<out BotController>, BotController> = mutableMapOf()
}
