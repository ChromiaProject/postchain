package net.postchain.gtv.json.gson

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import net.postchain.common.toHex
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvException
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvNull
import net.postchain.gtv.GtvString
import net.postchain.gtv.GtvType
import net.postchain.gtv.json.GtvJsonCodec
import net.postchain.gtv.json.GtvJsonConfig
import net.postchain.gtv.json.GtvJsonSupport
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets

/**
 * A [GtvJsonCodec] built on Gson.
 *
 * This is the codec whose bytes the others are measured against: the JSON GTV has always been written in is
 * whatever `make_gtv_gson()` produced, so the mapping is expressed here in terms of Gson's own writer rather than
 * reconstructed. [GtvJsonConfig.gsonCompatible] likewise reads through Gson's lenient parser instead of the
 * transcription of it the other codecs share.
 *
 * ```
 * val json = GtvJson.Default.encodeToString(gtv)
 * val gtv = GtvJson.Default.decodeFromString(json)
 * ```
 */
class GtvJson(
    private val config: GtvJsonConfig = GtvJsonConfig.Default,
    private val prettyPrint: Boolean = false,
) : GtvJsonCodec {

    private val gson: Gson = GsonBuilder()
        .serializeNulls()
        .also { if (!config.htmlSafe) it.disableHtmlEscaping() }
        .also { if (prettyPrint) it.setPrettyPrinting() }
        .create()

    override fun encodeToString(gtv: Gtv): String {
        val writer = StringWriter()
        gson.toJson(toJsonElement(gtv), writer)
        return writer.toString()
    }

    override fun encodeTo(gtv: Gtv, output: OutputStream) {
        // Not `use`: closing the writer would close the caller's stream underneath it.
        val writer = OutputStreamWriter(output, StandardCharsets.UTF_8)
        gson.toJson(toJsonElement(gtv), writer)
        writer.flush()
    }

    override fun decodeFromString(text: String): Gtv = read(JsonReader(text.reader()))

    override fun decodeFrom(input: InputStream): Gtv =
        read(JsonReader(InputStreamReader(input, StandardCharsets.UTF_8)))

    private fun read(reader: JsonReader): Gtv {
        val element = try {
            reader.strictness = if (config.gsonCompatible) Strictness.LENIENT else Strictness.STRICT
            if (reader.peek() == JsonToken.END_DOCUMENT) throw GtvException("Empty JSON input")
            // The whole document becomes a tree before any of it becomes a Gtv, because that is the order
            // Gson works in and the order is observable: a repeated field name discards the earlier value
            // here, so `{"a": 1.5, "a": 1}` never asks what 1.5 means as a GTV integer.
            val parsed = JsonParser.parseReader(reader)
            // Gson restores the reader's strictness before checking that the document is spent, so leniency
            // covers the value and nothing after it: `'a'#c` is a comment that arrives too late.
            reader.strictness = Strictness.LEGACY_STRICT
            if (reader.peek() != JsonToken.END_DOCUMENT) throw GtvException("Extra data at end of JSON")
            parsed
        } catch (e: GtvException) {
            throw e
        } catch (e: Exception) {
            throw GtvException(e.message ?: "Malformed JSON")
        }
        return fromJsonElement(element)
    }

    private fun toJsonElement(gtv: Gtv): JsonElement = when (gtv.type) {
        GtvType.NULL -> JsonNull.INSTANCE
        GtvType.INTEGER -> JsonPrimitive(gtv.asInteger())
        GtvType.STRING -> JsonPrimitive(gtv.asString())
        GtvType.BYTEARRAY -> JsonPrimitive(gtv.asByteArray().toHex())

        GtvType.ARRAY -> JsonArray().apply {
            for (element in gtv.asArray()) add(toJsonElement(element))
        }

        GtvType.DICT -> JsonObject().apply {
            for ((key, value) in gtv.asDict()) add(key, toJsonElement(value))
        }

        GtvType.BIGINTEGER -> {
            if (!config.supportBigInteger) GtvJsonSupport.bigIntegerUnsupported()
            val value = gtv.asBigInteger()
            if (config.bigIntegerAsString) JsonPrimitive(value.toString()) else JsonPrimitive(value)
        }
    }

    private fun fromJsonElement(element: JsonElement): Gtv = when {
        element.isJsonNull -> GtvNull
        element.isJsonArray -> GtvArray(element.asJsonArray.map { fromJsonElement(it) }.toTypedArray())
        element.isJsonObject ->
            GtvDictionary.build(element.asJsonObject.entrySet().associate { it.key to fromJsonElement(it.value) })

        else -> fromPrimitive(element.asJsonPrimitive)
    }

    private fun fromPrimitive(primitive: JsonPrimitive): Gtv = when {
        primitive.isBoolean -> GtvInteger(if (primitive.asBoolean) 1L else 0L)
        // The raw token text rather than a typed number, so the range rule matches the other codecs exactly.
        primitive.isNumber -> GtvJsonSupport.integerFromJsonNumber(primitive.asString)
        else -> GtvString(primitive.asString)
    }

    companion object {
        /** Rejects `big_integer`. Writes what `make_gtv_gson()` writes. */
        val Default: GtvJson = GtvJson(GtvJsonConfig.Default)

        /** Serializes `big_integer` as a JSON string. Writes what `makeStrictGtvGson()` writes. */
        val Strict: GtvJson = GtvJson(GtvJsonConfig.Strict)

        /** Serializes `big_integer` as a bare JSON number. Writes what `makeLenientGtvGson()` writes. */
        val Lenient: GtvJson = GtvJson(GtvJsonConfig.Lenient)

        /** [Lenient], indented with two spaces. */
        val LenientPretty: GtvJson = GtvJson(GtvJsonConfig.Lenient, prettyPrint = true)
    }
}
