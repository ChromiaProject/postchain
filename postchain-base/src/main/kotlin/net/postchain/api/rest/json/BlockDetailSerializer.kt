// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.api.rest.json

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import net.postchain.base.BaseBlockWitness
import net.postchain.common.toHex
import net.postchain.core.block.BlockDetail
import net.postchain.gtv.makeStrictGtvGson
import java.lang.reflect.Type

internal class BlockDetailSerializer : JsonSerializer<BlockDetail> {

    val gson = makeStrictGtvGson()

    override fun serialize(
            src: BlockDetail?,
            typeOfSrc: Type?,
            context: JsonSerializationContext?
    ): JsonElement {
        if (src == null) {
            return JsonNull.INSTANCE
        }

        val json = JsonObject()
        json.add("rid", JsonPrimitive(src.rid.toHex()))
        json.add("prevBlockRID", JsonPrimitive(src.prevBlockRID.toHex()))
        json.add("header", JsonPrimitive(src.header.toHex()))
        json.add("height", JsonPrimitive(src.height))
        val transactions = JsonArray()
        src.transactions.forEach {
            val tx = JsonObject()
            tx.add("rid", JsonPrimitive(it.rid.toHex()))
            tx.add("hash", JsonPrimitive(it.hash.toHex()))
            it.data?.let { data ->
                tx.add("data", JsonPrimitive(data.toHex()))
            }
            transactions.add(tx)
        }
        json.add("transactions", transactions)
        json.add("witness", JsonPrimitive(src.witness.toHex()))
        val witnesses = JsonArray()
        val witnessSignatures = JsonArray()
        BaseBlockWitness.fromBytes(src.witness).getSignatures().forEach {
            witnesses.add(it.subjectID.toHex())
            witnessSignatures.add(it.data.toHex())
        }
        json.add("witnesses", witnesses)
        json.add("witnessSignatures", witnessSignatures)
        json.add("timestamp", JsonPrimitive(src.timestamp))
        
        return json
    }
}
