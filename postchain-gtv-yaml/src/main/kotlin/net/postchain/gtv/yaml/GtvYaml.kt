package net.postchain.gtv.yaml

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import net.postchain.common.types.WrappedByteArray
import net.postchain.gtv.Gtv
import java.io.File
import java.math.BigInteger

class GtvYaml(init: ObjectMapper.() -> Unit = {}) {

    val mapper: ObjectMapper = ObjectMapper(YAMLFactory()
            .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES))
            .registerKotlinModule()
            .registerModule(SimpleModule().apply {
                addSerializer(Gtv::class.java, GtvSerializer())
                addSerializer(ByteArray::class.java, ByteArraySerializer())
                addSerializer(BigInteger::class.java, BigIntegerSerializer())
                addDeserializer(Gtv::class.java, GtvDeserializer())
                addDeserializer(ByteArray::class.java, ByteArrayDeserializer())
                addDeserializer(WrappedByteArray::class.java, WrappedByteArrayDeserializer())
                addDeserializer(BigInteger::class.java, BigIntegerDeserializer())
            })
            .also(init)

    inline fun <reified T> load(content: String): T = mapper.readValue(content, T::class.java)

    fun load(content: String) = load<Gtv>(content)

    fun <T> dump(obj: T) = mapper.writeValueAsString(obj)

    inline fun <reified T> load(src: File): T = mapper.readValue(src, T::class.java)

    fun load(src: File) = load<Gtv>(src)

    fun <T> dump(obj: T, dst: File) = mapper.writeValue(dst, obj)
}
