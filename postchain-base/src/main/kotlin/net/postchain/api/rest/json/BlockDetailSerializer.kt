// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.api.rest.json

import com.google.gson.*
import net.postchain.base.BaseBlockWitness
import net.postchain.common.toHex
import net.postchain.core.block.BlockDetail
import net.postchain.gtv.make_gtv_gson
import java.lang.reflect.Type

internal class BlockDetailSerializer : JsonSerializer<BlockDetail> {

    val gson = make_gtv_gson()

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
            if (it.data != null) {
                tx.add("data", JsonPrimitive(it.data.toHex()))
            }
            transactions.add(tx)
        }
        json.add("transactions", transactions)
        json.add("witness", JsonPrimitive(src.witness.toHex()))
        val witnesses = JsonArray()
        BaseBlockWitness.fromBytes(src.witness).getSignatures().forEach {
            witnesses.add(it.subjectID.toHex())
        }
        json.add("witnesses", witnesses)
        json.add("timestamp", JsonPrimitive(src.timestamp))
        
        return json
    }
}
