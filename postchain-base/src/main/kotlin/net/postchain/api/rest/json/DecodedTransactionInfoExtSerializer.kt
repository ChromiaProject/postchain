// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.api.rest.json

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import net.postchain.api.rest.model.DecodedTransactionInfoExt
import net.postchain.base.BaseBlockWitness
import net.postchain.common.toHex
import net.postchain.gtv.makeStrictGtvGson
import net.postchain.gtx.Gtx
import java.lang.reflect.Type

internal class DecodedTransactionInfoExtSerializer : JsonSerializer<DecodedTransactionInfoExt> {

    val gson = makeStrictGtvGson()

    override fun serialize(
            src: DecodedTransactionInfoExt?,
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
        val witnesses = JsonArray()
        val witnessSignatures = JsonArray()
        BaseBlockWitness.fromBytes(src.witness).getSignatures().forEach {
            witnesses.add(it.subjectID.toHex())
            witnessSignatures.add(it.data.toHex())
        }
        json.add("witnesses", witnesses)
        json.add("witnessSignatures", witnessSignatures)
        json.add("timestamp", JsonPrimitive(src.timestamp))
        json.add("txRID", JsonPrimitive(src.txRID.toHex()))
        json.add("txHash", JsonPrimitive(src.txHash.toHex()))
        src.txData?.let { txData ->
            json.add("tx", decodeTxData(txData))
        }
        return json
    }

    private fun decodeTxData(txData: ByteArray): JsonElement {
        val tx = Gtx.decode(txData)
        val json = JsonObject()
        val operations = JsonArray()
        for (op in tx.gtxBody.operations) {
            val opJson = JsonObject()
            opJson.addProperty("name", op.opName)
            val argsJson = JsonArray()
            for (arg in op.args) {
                argsJson.add(gson.toJsonTree(arg))
            }
            opJson.add("args", argsJson)
            operations.add(opJson)
        }
        json.add("operations", operations)
        val signers = JsonArray()
        for (signer in tx.gtxBody.signers) {
            signers.add(signer.toHex())
        }
        json.add("signers", signers)
        return json
    }
}
