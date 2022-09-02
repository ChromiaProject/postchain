// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.json

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.postchain.api.rest.model.ApiStatus
import net.postchain.api.rest.model.ApiTx
import net.postchain.api.rest.model.GTXQuery
import net.postchain.base.ConfirmationProof
import net.postchain.core.TransactionInfoExt
import net.postchain.core.block.BlockDetail

object JsonFactory {

    fun makeJson(): Gson = buildGson(false)

    fun makePrettyJson(): Gson = buildGson(true)

    private fun buildGson(pretty: Boolean): Gson {
        return GsonBuilder()
            .registerTypeAdapter(ConfirmationProof::class.java, ConfirmationProofSerializer())
            .registerTypeAdapter(ApiTx::class.java, TransactionDeserializer())
            .registerTypeAdapter(ApiStatus::class.java, ApiStatusSerializer())
            .registerTypeAdapter(GTXQuery::class.java, GTXQueryDeserializer())
            .registerTypeAdapter(TransactionInfoExt::class.java, TransactionInfoExtSerializer())
            .registerTypeAdapter(BlockDetail::class.java, BlockDetailSerializer())
            .apply {
                if (pretty) setPrettyPrinting()
            }
            .create()!!
    }
}