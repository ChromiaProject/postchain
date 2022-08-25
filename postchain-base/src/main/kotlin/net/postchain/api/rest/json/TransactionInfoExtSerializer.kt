// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.api.rest.json

import com.google.gson.*
import net.postchain.common.toHex
import net.postchain.core.TransactionInfoExt
import net.postchain.gtv.make_gtv_gson
import java.lang.reflect.Type

internal class TransactionInfoExtSerializer : JsonSerializer<TransactionInfoExt> {

    val gson = make_gtv_gson()

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
        if (src.txData != null) {
            json.add("txData", JsonPrimitive(src.txData.toHex()))
        }
        return json
    }
}
