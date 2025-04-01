// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.json

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import net.postchain.api.rest.model.ApiStatus
import java.lang.reflect.Type

internal class ApiStatusSerializer : JsonSerializer<ApiStatus> {

    override fun serialize(src: ApiStatus?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement =
            JsonObject().apply {
                add("status", JsonPrimitive(src!!.status))
                src.rejectReason?.let { add("rejectReason", JsonPrimitive(it)) }
                src.rejectTimestamp?.let { add("rejectTimestamp", JsonPrimitive(it)) }
            }
}
