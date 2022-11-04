// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv

import com.google.gson.*
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.toHex
import net.postchain.gtv.GtvFactory.gtv
import java.lang.reflect.Type

const val NUMBER_ERROR_MSG = "Could not deserialize number, valid numbers must be integers and be in range: [-2^63, (2^63)-1]"

class GtvAdapter : JsonDeserializer<Gtv>, JsonSerializer<Gtv> {

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Gtv {
        if (json.isJsonPrimitive) {
            val prim = json.asJsonPrimitive
            return if (prim.isBoolean)
                gtv(if (prim.asBoolean) 1L else 0L)
            else if (prim.isNumber)
                try {
                    gtv(prim.asBigDecimal.longValueExact())
                } catch (e: ArithmeticException) {
                    throw ProgrammerMistake(NUMBER_ERROR_MSG, e)
                }
            else if (prim.isString)
                gtv(prim.asString)
            else throw ProgrammerMistake("Can't deserialize JSON primitive")
        } else if (json.isJsonArray) {
            val arr = json.asJsonArray
            return gtv(*arr.map({ deserialize(it, typeOfT, context) }).toTypedArray())
        } else if (json.isJsonNull) {
            return GtvNull
        } else if (json.isJsonObject) {
            val obj = json.asJsonObject
            val mut = mutableMapOf<String, Gtv>()
            obj.entrySet().forEach {
                mut[it.key] = deserialize(it.value, typeOfT, context)
            }
            return gtv(mut)
        } else throw ProgrammerMistake("Could not deserialize JSON element")
    }

    private fun encodeDict(d: Gtv, t: Type, c: JsonSerializationContext): JsonObject {
        val o = JsonObject()
        for ((k, v) in d.asDict()) {
            o.add(k, serialize(v, t, c))
        }
        return o
    }

    private fun encodeArray(d: Gtv, t: Type, c: JsonSerializationContext): JsonArray {
        val a = JsonArray()
        for (v in d.asArray()) {
            a.add(serialize(v, t, c))
        }
        return a
    }

    override fun serialize(v: Gtv, t: Type, c: JsonSerializationContext): JsonElement {
        when (v.type) {
            GtvType.INTEGER -> return JsonPrimitive(v.asInteger())
            GtvType.STRING -> return JsonPrimitive(v.asString())
            GtvType.NULL -> return JsonNull.INSTANCE
            GtvType.BYTEARRAY -> return JsonPrimitive(v.asByteArray().toHex())
            GtvType.DICT -> return encodeDict(v, t, c)
            GtvType.ARRAY -> return encodeArray(v, t, c)
            else -> throw IllegalStateException("Should have taken care of all cases, type not known: ${v.type}" )
        }
    }
}

fun make_gtv_gson(): Gson {
    return GsonBuilder().
            registerTypeAdapter(Gtv::class.java, GtvAdapter()).
            serializeNulls().
            create()!!
}

fun gtvToJSON(gtvData: Gtv, gson: Gson): String {
    return gson.toJson(gtvData, Gtv::class.java)
}