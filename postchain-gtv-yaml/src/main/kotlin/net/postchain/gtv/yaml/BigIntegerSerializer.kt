package net.postchain.gtv.yaml

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import java.math.BigInteger

class BigIntegerSerializer: JsonSerializer<BigInteger>() {
    override fun serialize(v: BigInteger, generator: JsonGenerator, serializerProvider: SerializerProvider?) {
        generator.writeString("${v}L")
    }
}
