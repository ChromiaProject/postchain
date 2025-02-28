// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.json

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.postchain.api.rest.BlockSignature
import net.postchain.api.rest.json.JsonFactory.gsonBuilder
import net.postchain.api.rest.model.ApiStatus
import net.postchain.api.rest.model.TxRid
import net.postchain.base.ConfirmationProof
import net.postchain.core.TransactionInfoExt
import net.postchain.core.block.BlockDetail
import org.http4k.format.ConfigurableGson

object JsonFactory : ConfigurableGson(gsonBuilder(false)) {

    fun makeJson(): Gson = gsonBuilder(false).create()!!

    fun makePrettyJson(): Gson = gsonBuilder(true).create()!!

    fun makeCustomJson(
            pretty: Boolean = true,
            namingConvention: FieldNamingPolicy = FieldNamingPolicy.LOWER_CASE_WITH_DASHES
    ): Gson = gsonBuilder(pretty).apply {
        setFieldNamingPolicy(namingConvention)
    }.create()!!

    @JvmStatic
    private fun gsonBuilder(pretty: Boolean): GsonBuilder = GsonBuilder()
            .registerTypeAdapter(ConfirmationProof::class.java, ConfirmationProofSerializer())
            .registerTypeAdapter(ApiStatus::class.java, ApiStatusSerializer())
            .registerTypeAdapter(TransactionInfoExt::class.java, TransactionInfoExtSerializer())
            .registerTypeAdapter(BlockDetail::class.java, BlockDetailSerializer())
            .registerTypeAdapter(BlockSignature::class.java, BlockSignatureSerializer())
            .registerTypeAdapter(TxRid::class.java, TxRidSerializer())
            .apply {
                if (pretty) setPrettyPrinting()
            }
}
