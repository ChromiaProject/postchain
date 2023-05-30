// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.json

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.postchain.api.rest.json.JsonFactory.gsonBuilder
import net.postchain.api.rest.model.ApiStatus
import net.postchain.api.rest.model.GTXQuery
import net.postchain.base.ConfirmationProof
import net.postchain.core.TransactionInfoExt
import net.postchain.core.block.BlockDetail
import org.http4k.format.ConfigurableGson

object JsonFactory : ConfigurableGson(gsonBuilder(false)) {

    fun makeJson(): Gson = buildGson(false)

    fun makePrettyJson(): Gson = buildGson(true)

    private fun buildGson(pretty: Boolean): Gson = gsonBuilder(pretty)
            .create()!!

    @JvmStatic
    private fun gsonBuilder(pretty: Boolean): GsonBuilder = GsonBuilder()
            .registerTypeAdapter(ConfirmationProof::class.java, ConfirmationProofSerializer())
            .registerTypeAdapter(ApiStatus::class.java, ApiStatusSerializer())
            .registerTypeAdapter(GTXQuery::class.java, GTXQueryDeserializer())
            .registerTypeAdapter(TransactionInfoExt::class.java, TransactionInfoExtSerializer())
            .registerTypeAdapter(BlockDetail::class.java, BlockDetailSerializer())
            .apply {
                if (pretty) setPrettyPrinting()
            }
}
