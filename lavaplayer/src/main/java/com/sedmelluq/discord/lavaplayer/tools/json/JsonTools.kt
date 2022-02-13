package com.sedmelluq.discord.lavaplayer.tools.json

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.serializer
import java.io.InputStream

@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
object JsonTools {
    val format = Json {
        isLenient = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /* stream deserialization */
    inline fun <reified T : Any> decode(stream: InputStream): T =
        format.decodeFromStream(T::class.serializer(), stream)

    fun <T : Any> decode(deserializer: DeserializationStrategy<T>, stream: InputStream): T =
        format.decodeFromStream(deserializer, stream)

    /* string deserialization */
    inline fun <reified T : Any> decode(json: String): T =
        format.decodeFromString(T::class.serializer(), json)

    fun <T : Any> decode(deserializer: DeserializationStrategy<T>, json: String): T =
        format.decodeFromString(deserializer, json)

    /* serialization */
    inline fun <reified T : Any> encode(thing: T): String =
        format.encodeToString(T::class.serializer(), thing)
}
