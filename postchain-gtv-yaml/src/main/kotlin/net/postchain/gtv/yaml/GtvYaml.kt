package net.postchain.gtv.yaml

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import net.postchain.common.types.WrappedByteArray
import net.postchain.gtv.Gtv
import java.io.File

class GtvYaml(init: ObjectMapper.() -> Unit = {}) {

    val mapper: ObjectMapper = ObjectMapper(YAMLFactory())
            .registerKotlinModule()
            .registerModule(SimpleModule().apply {
                addDeserializer(Gtv::class.java, GtvDeserializer())
                addDeserializer(ByteArray::class.java, ByteArrayDeserializer())
                addDeserializer(WrappedByteArray::class.java, WrappedByteArrayDeserializer())
            })
            .also(init)

    inline fun <reified T> load(content: String): T = mapper.readValue(content, T::class.java)
    fun load(content: String): Gtv = mapper.readValue(content, Gtv::class.java)

    inline fun <reified T> load(src: File): T = mapper.readValue(src, T::class.java)

    fun load(src: File): Gtv = mapper.readValue(src, Gtv::class.java)
}
