// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.json

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import net.postchain.base.ConfirmationProof
import net.postchain.common.toHex
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.make_gtv_gson
import net.postchain.gtv.mapper.GtvObjectMapper
import java.lang.reflect.Type

internal class ConfirmationProofSerializer : JsonSerializer<ConfirmationProof> {

    val gson = make_gtv_gson()

    override fun serialize(src: ConfirmationProof?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        val proof = JsonObject()
        if (src == null) {
            return proof
        }
        proof.addProperty("proof", GtvEncoder.encodeGtv(GtvObjectMapper.toGtvDictionary(src)).toHex())
        return proof
    }
}
