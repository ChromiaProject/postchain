// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.json

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import net.postchain.api.rest.BlockSignature
import net.postchain.common.toHex
import net.postchain.gtv.make_gtv_gson
import java.lang.reflect.Type

internal class BlockSignatureSerializer : JsonSerializer<BlockSignature> {

    val gson = make_gtv_gson()

    override fun serialize(src: BlockSignature?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return if (src == null) {
            JsonNull.INSTANCE
        } else
            JsonObject().apply {
                add("subjectID", JsonPrimitive(src.subjectID.toHex()))
                add("data", JsonPrimitive(src.data.toHex()))
            }
    }
}
