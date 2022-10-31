package net.postchain.client.core

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex

class ByteArrayTypeAdapter : TypeAdapter<ByteArray>() {
    override fun write(writer: JsonWriter, byteArray: ByteArray?) {
        if (byteArray == null) {
            writer.nullValue()
        } else {
            writer.value(byteArray.toHex())
        }
    }

    override fun read(reader: JsonReader): ByteArray? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        return reader.nextString().hexStringToByteArray()
    }
}
