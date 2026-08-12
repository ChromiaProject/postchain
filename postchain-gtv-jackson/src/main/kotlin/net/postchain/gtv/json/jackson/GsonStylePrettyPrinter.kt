package net.postchain.gtv.json.jackson

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.PrettyPrinter

/**
 * Two-space indentation matching Gson's `setPrettyPrinting()`, so both JSON backends produce identical text.
 *
 * Jackson's own [com.fasterxml.jackson.core.util.DefaultPrettyPrinter] differs in three visible ways: it puts a
 * space before the colon, renders empty containers as `[ ]` and `{ }`, and defaults to a different indent.
 */
internal class GsonStylePrettyPrinter : PrettyPrinter {

    private var depth = 0

    override fun writeRootValueSeparator(g: JsonGenerator) {
        g.writeRaw(' ')
    }

    override fun writeStartObject(g: JsonGenerator) {
        g.writeRaw('{')
        depth++
    }

    override fun beforeObjectEntries(g: JsonGenerator) = newline(g)

    override fun writeObjectFieldValueSeparator(g: JsonGenerator) {
        g.writeRaw(": ")
    }

    override fun writeObjectEntrySeparator(g: JsonGenerator) {
        g.writeRaw(',')
        newline(g)
    }

    override fun writeEndObject(g: JsonGenerator, nrOfEntries: Int) {
        depth--
        if (nrOfEntries > 0) newline(g)
        g.writeRaw('}')
    }

    override fun writeStartArray(g: JsonGenerator) {
        g.writeRaw('[')
        depth++
    }

    override fun beforeArrayValues(g: JsonGenerator) = newline(g)

    override fun writeArrayValueSeparator(g: JsonGenerator) {
        g.writeRaw(',')
        newline(g)
    }

    override fun writeEndArray(g: JsonGenerator, nrOfValues: Int) {
        depth--
        if (nrOfValues > 0) newline(g)
        g.writeRaw(']')
    }

    private fun newline(g: JsonGenerator) {
        g.writeRaw('\n')
        repeat(depth) { g.writeRaw("  ") }
    }
}
