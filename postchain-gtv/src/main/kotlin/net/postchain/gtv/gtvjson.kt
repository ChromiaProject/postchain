// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.toHex
import net.postchain.gtv.GtvFactory.gtv
import java.lang.reflect.Type
import java.math.BigDecimal

internal fun errorMsg(number: BigDecimal) = "Could not deserialize number '$number' to GtvInteger, valid numbers must be integers and be in range: [-2^63, (2^63)-1]"

/**
 * @param strict  serialize big_integer as string if true, as number if false
 */
class GtvAdapter(val strict: Boolean = true, val supportBigInteger: Boolean = true) : JsonDeserializer<Gtv>, JsonSerializer<Gtv> {
    constructor(strict: Boolean = true): this(strict, supportBigInteger = false)

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Gtv {
        if (json.isJsonPrimitive) {
            val prim = json.asJsonPrimitive
            return if (prim.isBoolean)
                gtv(if (prim.asBoolean) 1L else 0L)
            else if (prim.isNumber) {
                val number = prim.asBigDecimal
                try {
                    gtv(number.longValueExact())
                } catch (e: ArithmeticException) {
                    throw ProgrammerMistake(errorMsg(number), e)
                }
            } else if (prim.isString)
                gtv(prim.asString)
            else throw ProgrammerMistake("Can't deserialize JSON primitive")
        } else if (json.isJsonArray) {
            val arr = json.asJsonArray
            return gtv(*arr.map { deserialize(it, typeOfT, context) }.toTypedArray())
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

    override fun serialize(v: Gtv, t: Type, c: JsonSerializationContext): JsonElement = when (v.type) {
        GtvType.INTEGER -> JsonPrimitive(v.asInteger())
        GtvType.STRING -> JsonPrimitive(v.asString())
        GtvType.NULL -> JsonNull.INSTANCE
        GtvType.BYTEARRAY -> JsonPrimitive(v.asByteArray().toHex())
        GtvType.DICT -> encodeDict(v, t, c)
        GtvType.ARRAY -> encodeArray(v, t, c)
        GtvType.BIGINTEGER -> if (supportBigInteger) {
            if (strict)
                JsonPrimitive(v.asBigInteger().toString())
            else
                JsonPrimitive(v.asBigInteger())
        } else {
            throw IllegalStateException("big_integer cannot be serialized as JSON")
        }
    }
}

/**
 * Does not support BigInteger.
 */
fun make_gtv_gson_builder(): GsonBuilder = GsonBuilder()
        .registerTypeHierarchyAdapter(Gtv::class.java, GtvAdapter(strict = true, supportBigInteger = false))
        .serializeNulls()

/**
 * Serialize BigInteger as string.
 */
fun makeStrictGvtGsonBuilder(): GsonBuilder = GsonBuilder()
        .registerTypeHierarchyAdapter(Gtv::class.java, GtvAdapter(strict = true, supportBigInteger = true))
        .serializeNulls()

/**
 * Serialize BigInteger as number.
 */
fun makeLenientGtvGsonBuilder(): GsonBuilder = GsonBuilder()
        .registerTypeHierarchyAdapter(Gtv::class.java, GtvAdapter(strict = false, supportBigInteger = true))
        .serializeNulls()

/**
 * Does not support BigInteger.
 */
fun make_gtv_gson(): Gson = make_gtv_gson_builder().create()!!

/**
 * Serialize BigInteger as string.
 */
fun makeStrictGtvGson(): Gson = makeStrictGvtGsonBuilder().create()!!

/**
 * Serialize BigInteger as number.
 */
fun makeLenientGtvGson(): Gson = makeLenientGtvGsonBuilder().create()!!

fun gtvToJSON(gtvData: Gtv, gson: Gson): String = gson.toJson(gtvData, Gtv::class.java)
