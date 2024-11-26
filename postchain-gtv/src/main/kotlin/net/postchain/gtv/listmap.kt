package net.postchain.gtv

import net.postchain.common.types.WrappedByteArray
import java.math.BigInteger

/**
 * Recursively convert a structure of `List<Any?>`, `Map<String, Any?>` and primitives to `Gtv`.
 *
 * `null` is converted to `GtvNull`.
 *
 * @throws IllegalArgumentException if an unsupported type is encountered
 */
fun listMapAndPrimitivesToGtv(obj: Any?): Gtv = when (obj) {
    is List<*> -> GtvFactory.gtv(obj.map { listMapAndPrimitivesToGtv(it) })
    is Map<*, *> -> GtvFactory.gtv(obj.map { (it.key as String) to listMapAndPrimitivesToGtv(it.value) }.toMap())
    is Boolean -> GtvFactory.gtv(obj)
    is Int -> GtvFactory.gtv(obj.toLong())
    is Long -> GtvFactory.gtv(obj)
    is BigInteger -> GtvFactory.gtv(obj)
    is ByteArray -> GtvFactory.gtv(obj)
    is WrappedByteArray -> GtvFactory.gtv(obj)
    is String -> GtvFactory.gtv(obj)
    null -> GtvNull
    else -> throw IllegalArgumentException("Cannot convert object of type ${obj::class.simpleName} to GTV")
}

/**
 * Recursively convert `Gtv` to a structure of `List<Any?>`, `Map<String, Any?>` and primitives.
 *
 * `GtvNull` is converted to `null`.
 */
fun gtvToListMapAndPrimitives(gtv: Gtv): Any? = when (gtv.type) {
    GtvType.ARRAY -> gtv.asArray().map { gtvToListMapAndPrimitives(it) }
    GtvType.DICT -> gtv.asDict().mapValues { gtvToListMapAndPrimitives(it.value) }
    GtvType.INTEGER -> gtv.asInteger()
    GtvType.BIGINTEGER -> gtv.asBigInteger()
    GtvType.BYTEARRAY -> gtv.asByteArray()
    GtvType.STRING -> gtv.asString()
    GtvType.NULL -> null
}
