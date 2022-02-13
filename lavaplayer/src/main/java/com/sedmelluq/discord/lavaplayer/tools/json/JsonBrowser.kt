package com.sedmelluq.discord.lavaplayer.tools.json

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.IOException
import java.io.InputStream

@Serializable(with = JsonBrowserSerializer::class)
class JsonBrowser(@get:JvmName("element") val element: JsonElement) {
    companion object {
        @JvmField
        val NULL_BROWSER = JsonBrowser(JsonNull)

        @JvmStatic
        fun create(element: JsonElement): JsonBrowser =
            if (element is JsonNull) NULL_BROWSER else JsonBrowser(element)

        /**
         * Parse from string.
         * @param json The JSON object as a string
         * @return JsonBrowser instance for navigating in the result
         * @throws IOException When parsing the JSON failed
         */
        @JvmStatic
        fun parse(json: String): JsonBrowser =
            JsonTools.decode(json)

        /**
         * Parse from an input stream.
         * @param stream The input stream to parse.
         * @return JsonBrowser instance for navigating in the result
         * @throws IOException When parsing the JSON failed
         */
        @JvmStatic
        fun parse(stream: InputStream): JsonBrowser =
            JsonTools.decode(stream)
    }

    /**
     * Whether the current element is null.
     */
    val isNull: Boolean
        get() = element is JsonNull

    /**
     * Whether the current element is a json array.
     */
    val isList: Boolean
        get() = element is JsonArray

    /**
     * Whether the current element is a json object.
     */
    val isMap: Boolean
        get() = element is JsonObject

    @get:JvmName("text")
    val text: String?
        get() {
            if (element !is JsonNull) {
                if (element is JsonPrimitive) {
                    return element.content
                }

                return element.toString()
            }

            return null
        }

    @get:JvmName("safeText")
    val safeText: String
        get() = text ?: ""

    /**
     * Get an element at an index for a list value
     * @param index List index
     * @return JsonBrowser instance which wraps the value at the specified index
     */
    @JvmName("index")
    operator fun get(index: Int): JsonBrowser = if (element is JsonArray && index in element.indices) {
        create(element[index])
    } else {
        NULL_BROWSER
    }

    /**
     * Get an element by key from a map value
     * @param key Map key
     * @return JsonBrowser instance which wraps the value with the specified key
     */
    operator fun get(key: String): JsonBrowser = if (element is JsonObject) {
        create(element[key] ?: JsonNull)
    } else {
        NULL_BROWSER
    }

    fun values(): List<JsonBrowser> {
        val values = mutableListOf<JsonElement>()
        when (element) {
            is JsonArray -> values.addAll(element)
            is JsonObject -> values.addAll(element.values)
            else -> values.add(element)
        }

        return values.map { JsonBrowser(it) }
    }

    inline fun <reified T> cast(): T = JsonTools.format.decodeFromJsonElement(element)

    inline fun <reified T> cast(default: T): T = safeCast<T>() ?: default

    inline fun <reified T> safeCast(): T? = element.runCatching { cast<T>() }.getOrNull()

    fun format(): String = JsonTools.format.encodeToString(element)

    fun asLong(): Long = cast()

    fun asLong(default: Long): Long = cast(default)

    fun asBoolean(default: Boolean): Boolean = cast(default)

    override fun equals(other: Any?): Boolean {
        val otherBrowser = other as? JsonBrowser
            ?: return false

        return element == otherBrowser.element
    }
}
