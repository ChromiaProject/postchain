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
 * Set the default value for this property if missing in [Gtv].
 *
 * @param defaultLong Default value if target property is type [Long]
 * @param defaultString Default value if target property is type [String]
 * @param defaultByteArray Default value if target property is type [ByteArray]
 * @param defaultBoolean Default value if target property is type [Boolean]
 * @param defaultBigInteger Default value if target property is type [java.math.BigInteger]
 * @param defaultDecimal Default value if target property is type [java.math.BigDecimal]
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE_PARAMETER)
annotation class DefaultValue(val defaultLong: Long = 0,
                              val defaultString: String = "",
                              val defaultByteArray: ByteArray = [],
                              val defaultBoolean: Boolean = false,
                              val defaultBigInteger: String = "0",
                              val defaultDecimal: String = "0.0")

/**
 * Sets the path to this property if nested inside several [GtvDictionary].
 *
 * @param path Path to property as a list of strings
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE_PARAMETER)
annotation class Nested(vararg val path: String)

/**
 * Mark this propery as transient and thus not present in the gtv. Cannot be used together with [Name].
 *
 * @param mappedName name of this property in the supplied map
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE_PARAMETER)
annotation class Transient(val mappedName: String)

/**
 * Store a gtv-view of this object.
 *
 * [Note]: Must be of type [Gtv] and used exclusively
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE_PARAMETER)
annotation class RawGtv
