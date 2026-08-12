package net.postchain.gtv.json.jackson

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
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
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets

/**
 * A [GtvJsonCodec] built on Jackson's streaming API.
 *
 * Deliberately `jackson-core` only — no `jackson-databind`, so nothing here touches reflection.
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

    private val factory = JsonFactory().apply {
        // The codec is handed streams it does not own, so it must never close them.
        disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
        disable(JsonParser.Feature.AUTO_CLOSE_SOURCE)
        // Jackson uppercases \uXXXX escapes where Gson lowercases them.
        characterEscapes = LowercaseUnicodeEscapes
    }

    override fun encodeToString(gtv: Gtv): String {
        val writer = StringWriter()
        factory.createGenerator(writer).use { generator -> write(configured(generator), gtv) }
        return GtvJsonSupport.escapeGsonSpecials(writer.toString(), config.htmlSafe)
    }

    override fun encodeTo(gtv: Gtv, output: OutputStream) {
        // Encoding through a Writer rather than createGenerator(stream, UTF8) on purpose: Jackson's UTF-8
        // generator escapes astral code points as surrogate pairs, which would make the byte and String forms
        // disagree with each other and with the reference output. This still streams; no String is built.
        // Not `use`: closing the writer would close the caller's stream underneath it.
        val writer = OutputStreamWriter(output, StandardCharsets.UTF_8)
        val escaping = GtvJsonSupport.escapingWriter(writer, config.htmlSafe)
        factory.createGenerator(escaping).use { generator -> write(configured(generator), gtv) }
        escaping.flush()
        writer.flush()
    }

    override fun decodeFromString(text: String): Gtv =
        if (config.gsonCompatible) GtvJsonSupport.decodeGsonCompatible(text) else read(factory.createParser(text))

    override fun decodeFromByteArray(bytes: ByteArray): Gtv =
        if (config.gsonCompatible) decodeFromString(bytes.decodeToString()) else read(factory.createParser(bytes))

    /** Compatibility mode reads the stream to the end first: Gson's leniency needs to look further ahead. */
    override fun decodeFrom(input: InputStream): Gtv =
        if (config.gsonCompatible) {
            decodeFromString(input.readBytes().decodeToString())
        } else {
            read(factory.createParser(input))
        }

    private fun configured(generator: JsonGenerator): JsonGenerator {
        if (prettyPrint) generator.prettyPrinter = GsonStylePrettyPrinter()
        return generator
    }

    private fun read(parser: JsonParser): Gtv = parser.use {
        if (it.nextToken() == null) throw GtvException("Empty JSON input")
        val gtv = readValue(it)
        if (it.nextToken() != null) throw GtvException("Extra data at end of JSON")
        gtv
    }

    private fun write(generator: JsonGenerator, gtv: Gtv) {
        when (gtv.type) {
            GtvType.NULL -> generator.writeNull()
            GtvType.INTEGER -> generator.writeNumber(gtv.asInteger())
            GtvType.STRING -> generator.writeString(gtv.asString())
            GtvType.BYTEARRAY -> generator.writeString(gtv.asByteArray().toHex())

            GtvType.ARRAY -> {
                generator.writeStartArray()
                for (element in gtv.asArray()) write(generator, element)
                generator.writeEndArray()
            }

            GtvType.DICT -> {
                generator.writeStartObject()
                for ((key, value) in gtv.asDict()) {
                    generator.writeFieldName(key)
                    write(generator, value)
                }
                generator.writeEndObject()
            }

            GtvType.BIGINTEGER -> {
                if (!config.supportBigInteger) GtvJsonSupport.bigIntegerUnsupported()
                val value = gtv.asBigInteger()
                if (config.bigIntegerAsString) generator.writeString(value.toString()) else generator.writeNumber(value)
            }
        }
    }

    /** Reads the value the parser is currently positioned on. */
    private fun readValue(parser: JsonParser): Gtv = when (parser.currentToken()) {
        JsonToken.VALUE_NULL -> GtvNull
        JsonToken.VALUE_TRUE -> GtvInteger(1L)
        JsonToken.VALUE_FALSE -> GtvInteger(0L)
        JsonToken.VALUE_STRING -> GtvString(parser.text)

        // Read the raw text rather than a typed number, so the range rule matches the other codec exactly.
        JsonToken.VALUE_NUMBER_INT, JsonToken.VALUE_NUMBER_FLOAT -> GtvJsonSupport.integerFromJsonNumber(parser.text)

        JsonToken.START_ARRAY -> {
            val elements = mutableListOf<Gtv>()
            while (parser.nextToken() != JsonToken.END_ARRAY) elements.add(readValue(parser))
            GtvArray(elements.toTypedArray())
        }

        JsonToken.START_OBJECT -> {
            val entries = mutableMapOf<String, Gtv>()
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                val key = parser.currentName() ?: throw GtvException("Expected a field name in JSON object")
                parser.nextToken()
                entries[key] = readValue(parser)
            }
            GtvDictionary.build(entries)
        }

        else -> throw GtvException("Unexpected JSON token: ${parser.currentToken()}")
    }

    companion object {
        /** Rejects `big_integer`. */
        val Default: GtvJson = GtvJson(GtvJsonConfig.Default)

        /** Serializes `big_integer` as a JSON string. */
        val Strict: GtvJson = GtvJson(GtvJsonConfig.Strict)

        /** Serializes `big_integer` as a bare JSON number. */
        val Lenient: GtvJson = GtvJson(GtvJsonConfig.Lenient)

        /** [Lenient], indented with two spaces. */
        val LenientPretty: GtvJson = GtvJson(GtvJsonConfig.Lenient, prettyPrint = true)
    }
}
