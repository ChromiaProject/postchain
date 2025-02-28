// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.json

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import net.postchain.api.rest.model.TxRid
import net.postchain.common.toHex
import java.lang.reflect.Type

internal class TxRidSerializer : JsonSerializer<TxRid> {

    override fun serialize(src: TxRid?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement =
            JsonPrimitive(src!!.bytes.toHex())
}
