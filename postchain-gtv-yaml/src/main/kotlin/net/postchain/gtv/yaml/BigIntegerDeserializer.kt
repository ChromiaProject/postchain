package net.postchain.gtv.yaml

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import java.math.BigInteger

class BigIntegerDeserializer : JsonDeserializer<BigInteger>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): BigInteger {
        require(p.valueAsString.endsWith("L"))
        return BigInteger(p.valueAsString.dropLast(1))
    }
}
