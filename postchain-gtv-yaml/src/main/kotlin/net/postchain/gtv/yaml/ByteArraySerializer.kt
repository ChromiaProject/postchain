package net.postchain.gtv.yaml

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import net.postchain.common.toHex

class ByteArraySerializer: JsonSerializer<ByteArray>() {
    override fun serialize(v: ByteArray, generator: JsonGenerator, serializerProvider: SerializerProvider?) {
        v.toHex().let {
            generator.writeNumber("x\"$it\"")
        }
    }

}
