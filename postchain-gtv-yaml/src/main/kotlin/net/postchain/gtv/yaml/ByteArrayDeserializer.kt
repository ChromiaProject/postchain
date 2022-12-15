package net.postchain.gtv.yaml

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import net.postchain.common.hexStringToByteArray

class ByteArrayDeserializer : JsonDeserializer<ByteArray>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ByteArray {
        require(p.valueAsString.startsWith("x\""))
        return p.valueAsString.substringAfter("x\"").trimEnd('"').hexStringToByteArray()
    }
}
