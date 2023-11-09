package net.postchain.gtv.yaml

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvType

class GtvSerializer : JsonSerializer<Gtv>() {
    override fun serialize(gtv: Gtv, generator: JsonGenerator, provider: SerializerProvider?) {
        when (gtv.type) {
            GtvType.NULL -> generator.writeNull()
            GtvType.INTEGER -> generator.writeNumber(gtv.asInteger())
            GtvType.BIGINTEGER -> generator.writeNumber(gtv.asBigInteger())
            GtvType.STRING -> generator.writeString(gtv.asString())
            GtvType.BYTEARRAY -> ByteArraySerializer().serialize(gtv.asByteArray(), generator, provider)
            GtvType.ARRAY -> {
                generator.writeStartArray()
                gtv.asArray().forEach { serialize(it, generator, provider) }
                generator.writeEndArray()
            }
            GtvType.DICT -> {
                generator.writeStartObject()
                gtv.asDict().forEach { (k, v) ->
                    generator.writeFieldName(k)
                    serialize(v, generator, provider)
                }
                generator.writeEndObject()
            }
        }
    }

}
