package net.postchain.gtv.mapper

import net.postchain.common.BlockchainRid
import net.postchain.common.types.RowId
import net.postchain.common.types.WrappedByteArray
import net.postchain.common.wrap
import net.postchain.crypto.PubKey
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.GtvTypeException
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaGetter


/**
 * Convert a [GtvDictionary] to a kotlin class. See [GtvObjectMapper]
 *
 * @param transientMap If the mapped class has any transient fields, supply them in this map
 */
inline fun <reified T : Any> Gtv.toObject(transientMap: Map<String, Any> = mapOf()): T {
    return GtvObjectMapper.fromGtv(this, T::class, transientMap)
}

/**
 * Convert a [GtvArray] to a kotlin class. See [GtvObjectMapper]
 *
 * @param transientMap If the mapped class has any transient fields, supply them in this map
 */
inline fun <reified T : Any> Gtv.toList(transientMap: Map<String, Any> = mapOf()): List<T> {
    return GtvObjectMapper.fromArray(this, T::class, transientMap)
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
 * String    -   BigDecimal
 * ByteArray -   ByteArray
 * Array     -   List
 * Dict      -   Class
 * ```
 *
 * The target class must have a public primary constructor with one or several of the following annotations:
 * [Name], [Nullable], [DefaultValue], [Nested], [RawGtv], [Transient]
 */
object GtvObjectMapper {

    /**
     * Convert a [GtvArray] to a kotlin class. See [GtvObjectMapper]
     *
     * @param transientMap If the mapped class has any transient fields, supply them in this map
     */
    fun <T : Any> fromArray(gtv: Gtv, classType: KClass<T>, transientMap: Map<String, Any> = mapOf()) = fromArray(gtv, classType.java, transientMap)

    /**
     * Convert a [GtvArray] to a kotlin class. See [GtvObjectMapper]
     *
     * @param transientMap If the mapped class has any transient fields, supply them in this map
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> fromArray(gtv: Gtv, classType: Class<T>, transientMap: Map<String, Any> = mapOf()): List<T> {
        if (gtv !is GtvArray) throw IllegalArgumentException("Gtv must be array type")
        return gtv.array.map {
            when {
                it is GtvDictionary -> fromGtv(it, classType, transientMap)
                classType.typeParameters.isNotEmpty() -> throw IllegalArgumentException("Generics are not allowed")
                else -> classToValue(classType, it, transientMap)
            }
        } as List<T>
    }

    /**
     * Convert a [GtvDictionary] to a kotlin class. See [GtvObjectMapper]
     *
     * @param transientMap If the mapped class has any transient fields, supply them in this map
     */
    fun <T : Any> fromGtv(gtv: Gtv, classType: KClass<T>, transientMap: Map<String, Any> = mapOf()) = fromGtv(gtv, classType.java, transientMap)

    /**
     * Convert a [GtvDictionary] to a kotlin class. See [GtvObjectMapper]
     *
     * @param transientMap If the mapped class has any transient fields, supply them in this map
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> fromGtv(gtv: Gtv, classType: Class<T>, transientMap: Map<String, Any> = mapOf()): T {
        if (gtv !is GtvDictionary) throw IllegalArgumentException("Gtv must be dictionary type")
        if (classType.constructors.isEmpty()) throw IllegalArgumentException("Type $classType must have primary constructor")
        if (classType.constructors.size > 1) throw IllegalArgumentException("Type ${classType.name} must have a single primary constructor, found ${classType.constructors.map { it.parameters.map { p -> p.name } }}")
        val constructor = classType.constructors[0]
        val constructorParameters = constructor.parameters.map {
            annotatedParameterToValue(it, gtv, transientMap)
        }
        return try {
            constructor.newInstance(*constructorParameters.toTypedArray()) as T
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Constructor for ${classType.simpleName} with parameters $constructorParameters not found", e)
        }
    }

    fun <T : Any> toGtvArray(obj: T): GtvArray {
        requireAllowedAnnotations(obj)

        val gtv = when (obj) {
            is Map<*, *> -> {
                obj.map {
                    gtv(classToGtv(it.key!!), classToGtv(it.value!!))
                }
            }

            is Collection<*> -> obj.map { classToGtv(it!!) }
            else -> {
                getPrimaryConstructorParameters(obj).map { parameter ->
                    val v = obj::class.declaredMemberProperties.find { it.name == parameter.name }?.javaGetter?.invoke(obj)
                    v?.let { classToGtv(it) } ?: GtvNull
                }
            }
        }
        return gtv(gtv)
    }

    fun <T : Any> toGtvDictionary(obj: T): GtvDictionary {
        requireAllowedAnnotations(obj)
        val map = when (obj) {
            is Map<*, *> -> {
                obj.map { (key, value) ->
                    if (key !is String) throw IllegalArgumentException("Wrong type")
                    key to (value?.let { v -> classToGtv(v) { toGtvDictionary(it) } } ?: GtvNull)
                }
            }

            is List<*> -> throw IllegalArgumentException("List types not supported")
            is Set<*> -> throw IllegalArgumentException("Set types not supported")
            else -> {
                getPrimaryConstructorParameters(obj).map { parameter ->
                    val parameterValue = obj::class.declaredMemberProperties.find { it.name == parameter.name }?.javaGetter?.invoke(obj)
                    val gtv = parameterValue?.let { value -> classToGtv(value) { toGtvDictionary(it) } } ?: GtvNull
                    if (!parameter.hasAnnotation<Name>()) throw IllegalArgumentException("parameter ${parameter.name} must have Name annotation")
                    val name = parameter.findAnnotation<Name>()!!.name
                    name to gtv
                }
            }
        }.toMap()
        return gtv(map)
    }

    private fun <T : Any> requireAllowedAnnotations(obj: T) {
        obj::class.constructors.first().parameters.forEach {
            require(!it.hasAnnotation<RawGtv>()) { "Raw Gtv Annotation not permitted" }
            require(!it.hasAnnotation<Transient>())
            require(!it.hasAnnotation<Nested>())
        }
    }
}

private fun <T : Any> getPrimaryConstructorParameters(obj: T): List<KParameter> {
    val constructorParameters = obj::class.primaryConstructor?.parameters
    if (constructorParameters.isNullOrEmpty()) {
        throw IllegalArgumentException("${obj::class.java} is not supported for mapping since it does not have any primary constructor parameters")
    }
    return constructorParameters
}

private fun classToGtv(obj: Any, other: (Any) -> Gtv = { GtvObjectMapper.toGtvArray(it) }): Gtv {
    return when {
        obj is ToGtv -> obj.toGtv()
        obj::class.java.isString() -> gtv(obj as String)
        obj::class.java.isLong() -> gtv(obj as Long)
        obj::class.java.isEnum -> gtv(obj.toString())
        obj::class.java.isBoolean() -> gtv(obj as Boolean)
        obj::class.java.isBigInteger() -> gtv(obj as BigInteger)
        obj::class.java.isBigDecimal() -> gtv((obj as BigDecimal).toString())
        obj::class.java.isByteArray() -> gtv(obj as ByteArray)
        obj::class.java.isWrappedByteArray() -> gtv(obj as WrappedByteArray)
        obj::class.java.isRowId() -> gtv((obj as RowId).id)
        obj::class.java.isPubkey() -> gtv((obj as PubKey).data)
        obj::class.java.isBlockchainRid() -> gtv((obj as BlockchainRid))
        obj is Collection<*> -> gtv(obj.map { classToGtv(it!!, other) })
        obj is Gtv -> obj
        else -> other(obj)
    }
}

private fun annotatedParameterToValue(param: Parameter, gtv: GtvDictionary, transient: Map<String, Any>): Any? {
    return when {
        param.isAnnotationPresent(Transient::class.java) -> transientParameterToValue(transient, param)
        param.isAnnotationPresent(RawGtv::class.java) -> gtv
        param.isAnnotationPresent(Nested::class.java) && param.isAnnotationPresent(Name::class.java) -> {
            val path = param.getAnnotation(Nested::class.java).path
            val gtvNode = path.fold(gtv) { acc, s ->
                if (acc[s] == null) return@fold gtv(mapOf())
                if (acc[s] !is GtvDictionary) throw IllegalArgumentException("Expected path $s to be GtvDictionary")
                acc[s] as GtvDictionary
            }
            return annotationToValue(gtvNode, param, transient)
        }

        param.isAnnotationPresent(Name::class.java) -> {
            return annotationToValue(gtv, param, transient)
        }

        else -> {
            throw IllegalArgumentException("No annotation for parameter ${param.name} is present.")
        }
    }
}

private fun transientParameterToValue(transient: Map<String, Any>, param: Parameter): Any? {
    val transientName = param.getAnnotation(Transient::class.java)!!.mappedName
    if (transient[transientName] == null && !param.isAnnotationPresent(Nullable::class.java)) throw IllegalArgumentException("Transient parameter \"$transientName\" is null, but not marked as nullable")
    return transient.get(transientName)
}

private fun annotationToValue(gtv: Gtv, param: Parameter, transient: Map<String, Any>): Any? {
    val name = param.getAnnotation(Name::class.java)?.name!!
    try {
        val gtvField = gtv[name]
        if (gtvField != null && !gtvField.isNull()) return parameterToValue(param, gtvField, transient)
        if (param.isAnnotationPresent(DefaultValue::class.java)) {
            val default = param.getAnnotation(DefaultValue::class.java)
            if (param.type.isPrimitiveType()) {
                return when {
                    param.type.isLong() -> default.defaultLong
                    param.type.isString() -> default.defaultString
                    param.type.isBoolean() -> default.defaultBoolean
                    param.type.isBigInteger() -> BigInteger(default.defaultBigInteger)
                    param.type.isBigDecimal() -> BigDecimal(default.defaultDecimal)
                    else -> default.defaultByteArray
                }
            }
            throw IllegalArgumentException("Default value not accepted for type: ${param.type}, must be Long, String or Bytearray")
        }
        if (param.isAnnotationPresent(Nullable::class.java)) {
            return null
        }
        throw IllegalArgumentException("Gtv is null, but field \"$name\" is neither marked with default nor nullable annotations")
    } catch (e: GtvTypeException) {
        throw GtvTypeException("Failed to decode field $name: ${e.message}")
    }
}

private fun parameterToValue(param: Parameter, gtv: Gtv?, transient: Map<String, Any>): Any? {
    if (gtv == null) return null
    if (param.parameterizedType is ParameterizedType) { // Collection types
        val parameterizedType = param.parameterizedType as ParameterizedType
        if (gtv is GtvDictionary) {
            val keyType = parameterizedType.actualTypeArguments[0].typeName
            if (keyType != String::class.java.name) throw IllegalArgumentException("Map key type must be String, but was $keyType")
            return gtv.dict.map { (t, u) ->
                t to classToValue(Class.forName(parameterizedType.actualTypeArguments[1].typeName), u, transient)
            }.toMap()
        }
        if (gtv !is GtvArray) throw IllegalArgumentException("Gtv must be array or Dict, but is ${gtv.type} with values $gtv")
        val listTypeArgument = parameterizedType.actualTypeArguments[0]
        return parameterizedTypeArgumentToValue(listTypeArgument, gtv, transient).let { if (param.type.isSet()) (it as List<*>).toSet() else it }
    }
    return classToValue(param.type, gtv, transient)
}

private fun parameterizedTypeArgumentToValue(type: Type, gtv: Gtv?, transient: Map<String, Any>): Any? {
    if (gtv == null) return null
    val gtvArray = gtv.asArray()
    if (type is WildcardType) { // List types
        return gtvArray.map { parameterizedTypeArgumentToValue((type.upperBounds[0] as ParameterizedType).actualTypeArguments[0], it, transient) }
    }
    if (type.typeName == "byte[]") return gtvArray.map { classToValue(ByteArray::class.java, it, transient) }
    return gtvArray.map { classToValue(Class.forName(type.typeName), it, transient) }
}

private fun classToValue(classType: Class<*>, gtv: Gtv?, transient: Map<String, Any>): Any? {
    if (gtv == null) return null
    return when {
        classType.isGtv() -> gtv
        classType.isEnum -> getEnumValue(classType.name, gtv.asString())
        classType.isLong() -> gtv.asInteger()
        classType.isString() -> gtv.asString()
        classType.isBoolean() -> gtv.asBoolean()
        classType.isByteArray() -> gtv.asByteArray()
        classType.isWrappedByteArray() -> gtv.asByteArray().wrap()
        classType.isPubkey() -> PubKey(gtv.asByteArray())
        classType.isBlockchainRid() -> BlockchainRid(gtv.asByteArray())
        classType.isRowId() -> RowId(gtv.asInteger())
        classType.isBigInteger() -> gtv.asBigInteger()
        classType.isBigDecimal() -> BigDecimal(gtv.asString())

        else -> {
            val companionObject = classType.kotlin.companionObjectInstance
            if (companionObject is FromGtv<*>) {
                companionObject.fromGtv(gtv)
            } else {
                if (gtv !is GtvDictionary) throw IllegalArgumentException("Gtv must be a dictionary, but is: ${gtv.type} with values $gtv")
                if (classType.constructors.isEmpty()) throw IllegalArgumentException("Type $classType must have primary constructor")
                val n = classType.constructors[0].parameters.map {
                    annotatedParameterToValue(it, gtv, transient)
                }
                classType.constructors[0].newInstance(*n.toTypedArray())
            }
        }
    }
}

fun getEnumValue(enumClassName: String, enumValue: String): Any {
    @Suppress("UNCHECKED_CAST") val enum = Class.forName(enumClassName).enumConstants as Array<Enum<*>>
    return enum.firstOrNull { it.name == enumValue }
            ?: throw IllegalArgumentException("invalid value '$enumValue' for enum $enumClassName")
}

private fun Class<*>.isPrimitiveType(): Boolean {
    return this.isLong() || this.isString() || this.isByteArray() || this.isBigInteger() || this.isBigDecimal() || this.isBoolean()
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

private fun Class<*>.isWrappedByteArray(): Boolean {
    return this == WrappedByteArray::class.java
}

private fun Class<*>.isPubkey() = this == PubKey::class.java

private fun Class<*>.isBlockchainRid() = this == BlockchainRid::class.java

private fun Class<*>.isRowId(): Boolean {
    return this == RowId::class.java
}

private fun Class<*>.isBigInteger(): Boolean {
    return this == BigInteger::class.java || this == java.math.BigInteger::class.java
}

private fun Class<*>.isBigDecimal(): Boolean {
    return this == BigDecimal::class.java || this == java.math.BigDecimal::class.java
}

private fun Class<*>.isBoolean(): Boolean {
    return this == Boolean::class.java || this == java.lang.Boolean::class.java
}

private fun Class<*>.isSet(): Boolean {
    return this == Set::class.java
}

private fun Class<*>.isGtv() = Gtv::class.java.isAssignableFrom(this)
