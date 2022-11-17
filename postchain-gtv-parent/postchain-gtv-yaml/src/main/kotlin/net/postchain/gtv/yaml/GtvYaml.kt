package net.postchain.gtv.yaml

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.JsonToken.*
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.std.NumberDeserializers.BooleanDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import net.postchain.common.hexStringToByteArray
import net.postchain.common.hexStringToWrappedByteArray
import net.postchain.common.types.WrappedByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull

class ByteArrayDeserializer : JsonDeserializer<ByteArray>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ByteArray {
        return p.valueAsString.substringAfter("0x").hexStringToByteArray()
    }
}

class WrappedByteArrayDeserializer : JsonDeserializer<WrappedByteArray>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): WrappedByteArray {
        return p.valueAsString.substringAfter("0x").hexStringToWrappedByteArray()
    }
}

class GtvDeserializer : JsonDeserializer<Gtv>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Gtv {

        if (p.currentToken.isBoolean) return gtv(BooleanDeserializer(Boolean::class.java, null).deserialize(p, ctxt))
        return when (p.currentToken) {
            VALUE_NUMBER_FLOAT -> gtv(p.text)
            VALUE_NUMBER_INT -> {
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
            VALUE_STRING -> gtv(p.valueAsString)
            START_ARRAY -> {
                val res = mutableListOf<Gtv>()
                var n: JsonToken? = p.nextToken()
                while (n != END_ARRAY) {
                    res.add(this.deserialize(p, ctxt))
                    n = p.nextToken()
                }
                return gtv(res)
            }
            START_OBJECT -> {
                val res = mutableMapOf<String, Gtv>()
                var n: JsonToken = p.nextToken()
                while (n != END_OBJECT) {
                    val key = p.text
                    p.nextToken()
                    res[key] = this.deserialize(p, ctxt)
                    n = p.nextToken()
                }
                return gtv(res)
            }
            else -> GtvNull
        }
    }

}

class GtvYaml {

    val mapper = ObjectMapper(YAMLFactory())
            .registerKotlinModule()
            .registerModule(SimpleModule().apply {
                addDeserializer(Gtv::class.java, GtvDeserializer())
                addDeserializer(ByteArray::class.java, ByteArrayDeserializer())
                addDeserializer(WrappedByteArray::class.java, WrappedByteArrayDeserializer())
            })
            .findAndRegisterModules()

    inline fun <reified T> load(s: String): T = mapper.readValue(s, T::class.java)
    fun load(s: String) = mapper.readValue(s, Gtv::class.java)
}