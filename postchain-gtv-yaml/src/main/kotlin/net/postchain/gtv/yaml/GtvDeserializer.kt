package net.postchain.gtv.yaml

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.deser.std.NumberDeserializers
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull

class GtvDeserializer : JsonDeserializer<Gtv>() {
    override fun getNullValue(ctxt: DeserializationContext): Gtv {
        return gtv(mapOf())
    }
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Gtv {

        if (p.currentToken.isBoolean) return gtv(NumberDeserializers.BooleanDeserializer(Boolean::class.java, null).deserialize(p, ctxt))
        return when (p.currentToken) {
            JsonToken.VALUE_NUMBER_FLOAT -> gtv(p.text)
            JsonToken.VALUE_NUMBER_INT -> {
                when {
                    p.text.startsWith("0x") -> gtv(ByteArrayDeserializer().deserialize(p, ctxt))
                    else -> {
                        try {
                            gtv(p.text.toLong())
                        } catch (e: Exception) {
                            gtv(p.text.toBigInteger())
                        }
                    }
                }
            }
            JsonToken.VALUE_STRING -> gtv(p.valueAsString)
            JsonToken.START_ARRAY -> {
                val res = mutableListOf<Gtv>()
                var n: JsonToken? = p.nextToken()
                while (n != JsonToken.END_ARRAY) {
                    res.add(this.deserialize(p, ctxt))
                    n = p.nextToken()
                }
                return gtv(res)
            }
            JsonToken.START_OBJECT -> {
                val res = mutableMapOf<String, Gtv>()
                var n: JsonToken = p.nextToken()
                while (n != JsonToken.END_OBJECT) {
                    val key = p.text
                    p.nextToken()
                    res[key] = this.deserialize(p, ctxt)
                    n = p.nextToken()
                }
                return gtv(res)
            }
            JsonToken.VALUE_NULL -> GtvNull
            else -> gtv(mapOf())
        }
    }
}
