package lavaplayer.demo.controller

import net.dv8tion.jda.api.entities.Message

interface BotCommandMappingHandler {
    fun commandNotFound(message: Message, name: String)
    fun commandWrongParameterCount(message: Message, name: String, usage: String, given: Int, required: Int)
    fun commandWrongParameterType(message: Message, name: String, usage: String, index: Int, value: String, expectedType: Class<*>)
    fun commandRestricted(message: Message, name: String)
    fun commandException(message: Message, name: String, throwable: Throwable)
}
