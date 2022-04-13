package net.postchain.gtv.mapper

/**
 * Sets the name of this property in a [GtvDictionary].
 *
 * @param name The name of this property
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE_PARAMETER)
annotation class Name(val name: String)

/**
 * Marks this property as nullable.
 *
 * [Note]: The property must also be marked as nullable using kotlin ? operator
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE_PARAMETER)
annotation class Nullable

/**
 * For primitive types, set the default value for this property if missing in [Gtv].
 *
 * @param defaultLong Default value if target property is type [Long] or [java.math.BigInteger]
 * @param defaultString Default value if target property is type [String]
 * @param defaultByteArray Default value if target property is type [ByteArray]
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE_PARAMETER)
annotation class DefaultValue(val defaultLong: Long = 0, val defaultString: String = "", val defaultByteArray: ByteArray = [])

/**
 * Sets the path to this property if nested inside several [GtvDictionary].
 *
 * @param path Path to property as a list of strings
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE_PARAMETER)
annotation class Nested(vararg val path: String)

/**
 * Store a gtv-view of this object.
 *
 * [Note]: Must be of type [Gtv] and used exclusively
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE_PARAMETER)
annotation class RawGtv
