package net.postchain.gtv.yaml

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import net.postchain.common.hexStringToWrappedByteArray
import net.postchain.common.types.WrappedByteArray

class WrappedByteArrayDeserializer : JsonDeserializer<WrappedByteArray>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): WrappedByteArray {
        require(p.valueAsString.startsWith("x\""))
        return p.valueAsString.substringAfter("x\"").trimEnd('"').hexStringToWrappedByteArray()
    }
}
