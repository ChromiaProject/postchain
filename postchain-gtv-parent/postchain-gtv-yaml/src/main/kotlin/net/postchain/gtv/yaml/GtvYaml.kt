package net.postchain.gtv.yaml

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import net.postchain.common.types.WrappedByteArray
import net.postchain.gtv.Gtv

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
