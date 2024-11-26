// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.api.rest.json

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import net.postchain.common.toHex
import net.postchain.core.TransactionInfoExt
import net.postchain.gtv.makeStrictGtvGson
import java.lang.reflect.Type

internal class TransactionInfoExtSerializer : JsonSerializer<TransactionInfoExt> {

    val gson = makeStrictGtvGson()

    override fun serialize(
        src: TransactionInfoExt?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        if (src == null) {
            return JsonNull.INSTANCE
        }

        val json = JsonObject()
        json.add("blockRID", JsonPrimitive(src.blockRID.toHex()))
        json.add("blockHeight", JsonPrimitive(src.blockHeight))
        json.add("blockHeader", JsonPrimitive(src.blockHeader.toHex()))
        json.add("witness", JsonPrimitive(src.witness.toHex()))
        json.add("timestamp", JsonPrimitive(src.timestamp))
        json.add("txRID", JsonPrimitive(src.txRID.toHex()))
        json.add("txHash", JsonPrimitive(src.txHash.toHex()))
        src.txData?.let { txData ->
            json.add("txData", JsonPrimitive(txData.toHex()))
        }
        return json
    }
}
