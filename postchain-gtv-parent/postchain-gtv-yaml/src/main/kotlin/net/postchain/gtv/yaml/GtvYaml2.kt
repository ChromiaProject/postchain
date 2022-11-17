package net.postchain.gtv.yaml

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken.*
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import jakarta.xml.bind.annotation.adapters.HexBinaryAdapter
import net.postchain.common.hexStringToByteArray
import net.postchain.common.hexStringToWrappedByteArray
import net.postchain.common.types.WrappedByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull

class ByteArrayDeserializer: JsonDeserializer<ByteArray>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ByteArray {
        return p.valueAsString.substringAfter("0x").hexStringToByteArray()
    }
}

class WrappedByteArrayDeserializer: JsonDeserializer<WrappedByteArray>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): WrappedByteArray {
        return p.valueAsString.substringAfter("0x").hexStringToWrappedByteArray()
    }
}

class GtvDeserializer: JsonDeserializer<Gtv>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Gtv {


HexBinaryAdapter::class.java
        p.isExpectedStartArrayToken
        return when(p.currentToken) {
            VALUE_NUMBER_INT -> gtv(p.valueAsInt.toLong())
            VALUE_STRING -> gtv(p.valueAsString)
            VALUE_FALSE -> gtv(false)
            VALUE_TRUE -> gtv(true)
            VALUE_NUMBER_FLOAT -> gtv(p.valueAsDouble.toString())
            else -> GtvNull
        }
    }

}

class GtvModule: SimpleModule() {

}

class GtvYaml2 {

    val mapper =  ObjectMapper(YAMLFactory())
                .registerKotlinModule()
                .registerModule(SimpleModule().apply {
                    addDeserializer(Gtv::class.java, GtvDeserializer())
                    addDeserializer(ByteArray::class.java, ByteArrayDeserializer())
                    addDeserializer(WrappedByteArray::class.java, WrappedByteArrayDeserializer())
                })
            .findAndRegisterModules()

    inline fun <reified T> load(s: String) = mapper.readValue(s, T::class.java)
}