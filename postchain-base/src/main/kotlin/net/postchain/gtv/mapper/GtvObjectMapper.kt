package net.postchain.gtv.mapper

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDictionary
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.math.BigInteger
import kotlin.reflect.KClass


/**
 * Convert a [GtvDictionary] to a kotlin class. See [GtvObjectMapper]
 */
inline fun <reified T : Any> Gtv.toObject(): T {
    return GtvObjectMapper.fromGtv(this, T::class)
}

/**
 * Convert a [GtvArray] to a kotlin class. See [GtvObjectMapper]
 */
inline fun <reified T : Any> Gtv.toList(): List<T> {
    return GtvObjectMapper.fromArray(this, T::class)
}

/**
 * Maps [Gtv] to kotlin objects.
 *
 * The following mapping between data types will be performed:
 * ```
 * gtv      <->  kotlin
 * Integer   -   Long
 * Integer   -   BigInteger
 * Integer   -   Boolean
 * String    -   String
 * ByteArray -   ByteArray
 * Array     -   List
 * Dict      -   Class
 * ```
 *
 * The target class must have a public primary constructor with one or several of the following annotations:
 * [Name], [Nullable], [DefaultValue], [Nested], [RawGtv]
 */
object GtvObjectMapper {

    /**
     * Convert a [GtvArray] to a kotlin class. See [GtvObjectMapper]
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> fromArray(gtv: Gtv, classType: KClass<T>) = fromArray(gtv, classType.java)

    /**
     * Convert a [GtvArray] to a kotlin class. See [GtvObjectMapper]
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> fromArray(gtv: Gtv, classType: Class<T>): List<T> {
        if (gtv !is GtvArray) throw IllegalArgumentException("Gtv must be array type")
        return gtv.array.map {
            when {
                it is GtvDictionary -> fromGtv(it, classType)
                classType.typeParameters.isNotEmpty() -> throw IllegalArgumentException("Generics are not allowed")
                else -> classToValue(classType, it)
            }
        } as List<T>
    }

    /**
     * Convert a [GtvDictionary] to a kotlin class. See [GtvObjectMapper]
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> fromGtv(gtv: Gtv, classType: KClass<T>) = fromGtv(gtv, classType.java)

    /**
     * Convert a [GtvDictionary] to a kotlin class. See [GtvObjectMapper]
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> fromGtv(gtv: Gtv, classType: Class<T>): T {
        if (gtv !is GtvDictionary) throw IllegalArgumentException("Gtv must be dictionary type")
        if (classType.constructors.isEmpty()) throw IllegalArgumentException("Type $classType must have primary constructor")
        if (classType.constructors.size > 1) throw IllegalArgumentException("Type ${classType.name} must have a single primary constructor, found ${classType.constructors.map { it.parameters.map { p -> p.name } }}")
        val constructor = classType.constructors[0]
        val constructorParameters = constructor.parameters.map {
            annotatedParameterToValue(it, gtv)
        }
        return try {
            constructor.newInstance(*constructorParameters.toTypedArray()) as T
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Constructor for parameters $constructorParameters not found")
        }
    }
}

private fun annotatedParameterToValue(param: Parameter, gtv: GtvDictionary): Any? {
    return when {
        param.isAnnotationPresent(RawGtv::class.java) -> gtv
        param.isAnnotationPresent(Nested::class.java) && param.isAnnotationPresent(Name::class.java) -> {
            val path = param.getAnnotation(Nested::class.java).path
            val gtvNode = path.fold(gtv) { acc, s ->
                if (acc[s] !is GtvDictionary) throw IllegalArgumentException("Expected path $s to be GtvDictionary")
                acc[s] as GtvDictionary
            }
            return annotationToValue(gtvNode, param)
        }
        param.isAnnotationPresent(Name::class.java) -> {
            return annotationToValue(gtv, param)
        }
        else -> {
            throw IllegalArgumentException("No annotation for parameter ${param.name} is present.")
        }
    }
}

private fun annotationToValue(gtv: Gtv, param: Parameter): Any? {
    val name = param.getAnnotation(Name::class.java)?.name!!
    val gtvField = gtv[name]
    if (gtvField != null && !gtvField.isNull()) return parameterToValue(param, gtvField)
    if (param.isAnnotationPresent(DefaultValue::class.java)) {
        val default = param.getAnnotation(DefaultValue::class.java)
        if (param.type.isPrimitiveType()) {
            return when {
                param.type.isLong() -> default.defaultLong
                param.type.isString() -> default.defaultString
                param.type.isBoolean() -> default.defaultBoolean
                param.type.isBigInteger() -> BigInteger(default.defaultBigInteger)
                else -> default.defaultByteArray
            }
        }
        throw IllegalArgumentException("Default value not accepted for type: ${param.type}, must be Long, String or Bytearray")
    }
    if (param.isAnnotationPresent(Nullable::class.java)) {
        return null
    }
    throw IllegalArgumentException("Gtv is null, but field \"$name\" is neither marked with default nor nullable annotations")
}

private fun parameterToValue(param: Parameter, gtv: Gtv?): Any? {
    if (gtv == null) return null
    if (param.parameterizedType is ParameterizedType) { // List types
        if (gtv !is GtvArray) throw IllegalArgumentException("Gtv must be array, but is ${gtv.type} with values $gtv")
        val listTypeArgument = (param.parameterizedType as ParameterizedType).actualTypeArguments[0]
        return parameterizedTypeArgumentToValue(listTypeArgument, gtv)
    }
    return classToValue(param.type, gtv)
}

private fun parameterizedTypeArgumentToValue(type: Type, gtv: Gtv?): Any? {
    if (gtv == null) return null
    val gtvArray = gtv.asArray()
    if (type is WildcardType) { // List types
        return gtvArray.map { parameterizedTypeArgumentToValue((type.upperBounds[0] as ParameterizedType).actualTypeArguments[0], it) }
    }
    if (type.typeName == "byte[]") return gtvArray.map { classToValue(ByteArray::class.java, it) }
    return gtvArray.map { classToValue(Class.forName(type.typeName), it) }
}

private fun classToValue(classType: Class<*>, gtv: Gtv?): Any? {
    if (gtv == null) return null
    return when {
        classType.isGtv() -> gtv
        classType.isLong() -> gtv.asInteger()
        classType.isString() -> gtv.asString()
        classType.isBoolean() -> gtv.asBoolean()
        classType.isByteArray() -> gtv.asByteArray()
        classType.isBigInteger() -> gtv.asBigInteger()
        else -> {
            if (gtv !is GtvDictionary) throw IllegalArgumentException("Gtv must be a dictionary, but is: ${gtv.type} with values $gtv")
            if (classType.constructors.isEmpty()) throw IllegalArgumentException("Type $classType must have primary constructor")
            val n = classType.constructors[0].parameters.map {
                annotatedParameterToValue(it, gtv)
            }
            classType.constructors[0].newInstance(*n.toTypedArray())
        }
    }
}

private fun Class<*>.isPrimitiveType(): Boolean {
    return this.isLong() || this.isString() || this.isByteArray() || this.isBigInteger() || this.isBoolean()
}

private fun Class<*>.isLong(): Boolean {
    return this == Long::class.java || this == java.lang.Long::class.java
}

private fun Class<*>.isString(): Boolean {
    return this == String::class.java || this == java.lang.String::class.java
}

private fun Class<*>.isByteArray(): Boolean {
    return this == ByteArray::class.java
}

private fun Class<*>.isBigInteger(): Boolean {
    return this == BigInteger::class.java || this == java.math.BigInteger::class.java
}

private fun Class<*>.isBoolean(): Boolean {
    return this == Boolean::class.java || this == java.lang.Boolean::class.java
}

private fun Class<*>.isGtv() = Gtv::class.java.isAssignableFrom(this)
