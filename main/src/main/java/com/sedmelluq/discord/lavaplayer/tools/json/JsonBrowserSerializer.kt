package com.sedmelluq.discord.lavaplayer.tools.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder

class JsonBrowserSerializer : KSerializer<JsonBrowser> {
    override val descriptor = JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): JsonBrowser {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        return JsonBrowser(element)
    }

    override fun serialize(encoder: Encoder, value: JsonBrowser) {
        (encoder as JsonEncoder).encodeJsonElement(value.element)
    }
}
