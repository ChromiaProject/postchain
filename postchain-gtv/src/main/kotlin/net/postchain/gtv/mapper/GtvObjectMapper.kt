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
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.isSubtypeOf
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
 * [Name], [Nullable], [DefaultValue], [DefaultEmpty], [Nested], [RawGtv], [Transient]
 */
object GtvObjectMapper {

    /**
     * Convert a [GtvArray] to a kotlin class. See [GtvObjectMapper]
     *
     * @param transientMap If the mapped class has any transient fields, supply them in this map
     */
    fun <T : Any> fromArray(gtv: Gtv, classType: Class<T>, transientMap: Map<String, Any> = mapOf()) = fromArray(gtv, classType.kotlin, transientMap)

    /**
     * Convert a [GtvArray] to a kotlin class. See [GtvObjectMapper]
     *
     * @param transientMap If the mapped class has any transient fields, supply them in this map
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> fromArray(gtv: Gtv, classType: KClass<T>, transientMap: Map<String, Any> = mapOf()): List<T> {
        if (gtv !is GtvArray) throw IllegalArgumentException("Gtv must be array type")
        return gtv.array.mapIndexed { i, it ->
            when {
                it is GtvDictionary -> fromGtv(it, classType, transientMap)
                classType.typeParameters.isNotEmpty() -> throw IllegalArgumentException("Generics are not allowed")
                else -> classToValue(classType.createType(), it, transientMap, "Array element [$i]")
            }
        } as List<T>
    }

    /**
     * Convert a [GtvDictionary] to a kotlin class.
     *
     * @param transientMap If the mapped class has any transient fields, supply them in this map
     */
    fun <T : Any> fromGtv(gtv: Gtv, classType: Class<T>, transientMap: Map<String, Any> = mapOf()) = fromGtv(gtv, classType.kotlin, transientMap)

    /**
     * Convert a [GtvDictionary] to a kotlin class. See [GtvObjectMapper]
     *
     * @param transientMap If the mapped class has any transient fields, supply them in this map
     */
    fun <T : Any> fromGtv(gtv: Gtv, classType: KClass<T>, transientMap: Map<String, Any> = mapOf()): T {
        if (gtv !is GtvDictionary) throw IllegalArgumentException("Gtv must be dictionary type")
        if (classType.constructors.isEmpty()) throw IllegalArgumentException("Type $classType must have primary constructor")
        if (classType.constructors.size > 1) throw IllegalArgumentException("Type ${classType.simpleName} must have a single primary constructor, found ${classType.constructors.map { it.parameters.map { p -> p.name } }}")
        val constructor = classType.constructors.first()
        val constructorParameters = constructor.parameters.map {
            annotatedParameterToValue(it, gtv, transientMap)
        }
        return try {
            constructor.call(*constructorParameters.toTypedArray())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Constructor for ${classType.simpleName} with parameters $constructorParameters not found", e)
        }
    }

    /**
     * Return the default value for a Kotlin class, according to annotations.
     */
    fun <T : Any> default(classType: KClass<T>): T = fromGtv(gtv(emptyMap()), classType)

    /**
     * Return the default value for a Kotlin class, according to annotations.
     */
    fun <T : Any> default(classType: Class<T>): T = fromGtv(gtv(emptyMap()), classType)

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
                    val name = parameter.findAnnotation<Name>()?.name
                            ?: parameter.name
                            ?: throw IllegalArgumentException("parameter ${parameter.name} must have Name annotation")
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
        throw IllegalArgumentException("${obj::class} is not supported for mapping since it does not have any primary constructor parameters")
    }
    return constructorParameters
}

private fun classToGtv(obj: Any, other: (Any) -> Gtv = { GtvObjectMapper.toGtvArray(it) }): Gtv {
    return when {
        obj is ToGtv -> obj.toGtv()
        obj::class.isString() -> gtv(obj as String)
        obj::class.isInteger() -> gtv((obj as Int).toLong())
        obj::class.isLong() -> gtv(obj as Long)
        obj::class.isEnum() -> gtv(obj.toString())
        obj::class.isBoolean() -> gtv(obj as Boolean)
        obj::class.isBigInteger() -> gtv(obj as BigInteger)
        obj::class.isBigDecimal() -> gtv((obj as BigDecimal).toString())
        obj::class.isByteArray() -> gtv(obj as ByteArray)
        obj::class.isWrappedByteArray() -> gtv(obj as WrappedByteArray)
        obj::class.isRowId() -> gtv((obj as RowId).id)
        obj::class.isPubkey() -> gtv((obj as PubKey).data)
        obj::class.isBlockchainRid() -> gtv((obj as BlockchainRid))
        obj is Collection<*> -> gtv(obj.map { classToGtv(it!!, other) })
        obj is Gtv -> obj
        else -> other(obj)
    }
}

private fun annotatedParameterToValue(param: KParameter, gtv: GtvDictionary, transient: Map<String, Any>): Any? {
    param.findAnnotation<Transient>()?.let { return transientParameterToValue(transient, param, it.mappedName) }

    if (param.hasAnnotation<RawGtv>()) return gtv

    param.findAnnotation<Nested>()?.let {
        val gtvNode = it.path.fold(gtv) { acc, s ->
            if (acc[s] == null) return@fold gtv(mapOf())
            if (acc[s] !is GtvDictionary) throw IllegalArgumentException("Expected path $s to be GtvDictionary")
            acc[s] as GtvDictionary
        }
        return annotationToValue(gtvNode, param, transient)
    }

    return annotationToValue(gtv, param, transient)
}

private fun transientParameterToValue(transient: Map<String, Any>, param: KParameter, transientName: String): Any? {
    if (transient[transientName] == null && !(param.type.isMarkedNullable || param.hasAnnotation<Nullable>()))
        throw IllegalArgumentException("Transient parameter \"$transientName\" is null, but not marked as nullable")
    return transient[transientName]
}

private fun annotationToValue(gtv: Gtv, param: KParameter, transient: Map<String, Any>): Any? {
    val name = param.findAnnotation<Name>()?.name
            ?: param.name
            ?: throw IllegalArgumentException("parameter ${param.name} must have Name annotation")
    try {
        val gtvField = gtv[name]
        if (gtvField != null && !gtvField.isNull()) return parameterToValue(param, gtvField, transient, name)
        param.findAnnotation<DefaultValue>()?.let { default ->
            return when {
                param.type.classifier.isLong() -> default.defaultLong
                param.type.classifier.isString() -> default.defaultString
                param.type.classifier.isBoolean() -> default.defaultBoolean
                param.type.classifier.isBigInteger() -> BigInteger(default.defaultBigInteger)
                param.type.classifier.isBigDecimal() -> BigDecimal(default.defaultDecimal)
                param.type.classifier.isByteArray() -> default.defaultByteArray
                param.type.classifier.isWrappedByteArray() -> default.defaultByteArray
                else -> throw IllegalArgumentException("Default value not accepted for type: ${param.type}, must be Long, BigInteger, BigDecimal, String, Boolean or Bytearray (field $name)")
            }
        }
        if (param.hasAnnotation<DefaultEmpty>()) {
            return when {
                param.type.classifier.isMap() -> mapOf<String, Gtv>()
                param.type.classifier.isList() -> listOf<Gtv>()
                param.type.classifier.isSet() -> setOf<Gtv>()
                else -> parameterToValue(param, gtv(mapOf()), transient, name)
            }
        }
        if (param.type.isMarkedNullable || param.hasAnnotation<Nullable>()) {
            return null
        }
        if (param.type.isSubtypeOf(Gtv::class.createType())) {
            return GtvNull
        }
        throw IllegalArgumentException("Gtv is null, but field \"$name\" is neither marked with default nor nullable")
    } catch (e: GtvTypeException) {
        throw GtvTypeException("Failed to decode field $name: ${e.message}")
    }
}

private fun parameterToValue(param: KParameter, gtv: Gtv?, transient: Map<String, Any>, context: String): Any? {
    if (gtv == null) return null
    if (param.type.arguments.isNotEmpty()) { // Collection types
        if (gtv is GtvDictionary) {
            if (param.type.arguments.size != 2)
                throw IllegalArgumentException("Map type must be have two type arguments, but was ${param.type.arguments.size}; context: $context")
            val keyType = param.type.arguments[0].type
            if (keyType != String::class.createType())
                throw IllegalArgumentException("Map key type must be String, but was $keyType; context: $context")
            val valueType = param.type.arguments[1].type!!
            return gtv.dict.map { (t, u) ->
                t to classToValue(valueType, u, transient, t)
            }.toMap()
        }
        if (gtv is GtvArray) {
            if (param.type.arguments.size != 1)
                throw IllegalArgumentException("Collection type must be have one type arguments, but was ${param.type.arguments.size}; context: $context")
            val elementType = param.type.arguments[0].type!!
            return parameterizedTypeArgumentToValue(elementType, gtv.array.toList(), transient, context).let { if (param.type.classifier.isSet()) (it as List<*>).toSet() else it }
        }
        throw IllegalArgumentException("Gtv must be array or Dict, but is ${gtv.type} with values $gtv; context: $context")
    }
    return classToValue(param.type, gtv, transient, context)
}

private fun parameterizedTypeArgumentToValue(type: KType, values: List<Gtv>, transient: Map<String, Any>, context: String): Any =
        if (type.arguments.isNotEmpty()) { // List types
            val listType = type.arguments[0].type
            if (listType != null) {
                values.map {
                    if (it !is GtvArray) throw IllegalArgumentException("Gtv must be array, but is ${it.type} with values $it; context: $context")
                    parameterizedTypeArgumentToValue(listType, it.asArray().toList(), transient, context)
                }
            } else {
                throw IllegalArgumentException("Unexpected type $type; context: $context")
            }
        } else if (type.classifier.isByteArray()) {
            values.map { classToValue(ByteArray::class.createType(), it, transient, context) }
        } else {
            values.mapIndexed { i, it -> classToValue(type, it, transient, "$context[$i]") }
        }

private fun classToValue(classType: KType, gtv: Gtv?, transient: Map<String, Any>, context: String): Any? {
    if (gtv == null) return null
    return when {
        classType.classifier.isGtv() -> gtv
        classType.classifier.isEnum() -> getEnumValue(classType, gtv.asString(), context)
        classType.classifier.isInteger() -> throw IllegalArgumentException("Int is too small to represent GTV integer, please use Long; context: $context")
        classType.classifier.isLong() -> gtv.asInteger()
        classType.classifier.isString() -> gtv.asString()
        classType.classifier.isBoolean() -> gtv.asBoolean()
        classType.classifier.isByteArray() -> gtv.asByteArray()
        classType.classifier.isWrappedByteArray() -> gtv.asByteArray().wrap()
        classType.classifier.isPubkey() -> PubKey(gtv.asByteArray())
        classType.classifier.isBlockchainRid() -> BlockchainRid(gtv.asByteArray())
        classType.classifier.isRowId() -> RowId(gtv.asInteger())
        classType.classifier.isBigInteger() -> gtv.asBigInteger()
        classType.classifier.isBigDecimal() -> BigDecimal(gtv.asString())

        else -> {
            val kClass = classType.classifier as KClass<*>
            val companionObject = kClass.companionObjectInstance
            if (companionObject is FromGtv<*>) {
                companionObject.fromGtv(gtv)
            } else {
                if (gtv !is GtvDictionary) throw IllegalArgumentException("Gtv must be a dictionary, but is: ${gtv.type} with values $gtv; context: $context")
                if (classType.classifier.isMap()) return gtv.dict
                if (kClass.constructors.isEmpty()) throw IllegalArgumentException("Type $classType must have primary constructor; context: $context")
                val n = kClass.constructors.first().parameters.map {
                    annotatedParameterToValue(it, gtv, transient)
                }
                kClass.constructors.first().call(*n.toTypedArray())
            }
        }
    }
}

val javaLangIntegerClass = java.lang.Integer::class
val javaLangLongClass = java.lang.Long::class
val javaLangBooleanClass = java.lang.Boolean::class
val javaLangStringClass = java.lang.String::class
val javaMathBigIntegerClass = java.math.BigInteger::class
val javaMathBigDecimalClass = java.math.BigDecimal::class

private fun KClassifier?.isLong(): Boolean = this == Long::class || this == javaLangLongClass

private fun KClassifier?.isInteger(): Boolean =
        this == Int::class || this == javaLangIntegerClass

private fun KClassifier?.isBoolean(): Boolean =
        this == Boolean::class || this == javaLangBooleanClass

private fun KClassifier?.isString(): Boolean =
        this == String::class || this == javaLangStringClass

private fun KClassifier?.isByteArray(): Boolean = this == ByteArray::class

private fun KClassifier?.isWrappedByteArray(): Boolean = this == WrappedByteArray::class

private fun KClassifier?.isPubkey() = this == PubKey::class

private fun KClassifier?.isBlockchainRid() = this == BlockchainRid::class

private fun KClassifier?.isRowId(): Boolean = this == RowId::class

private fun KClassifier?.isBigInteger(): Boolean =
        this == BigInteger::class || this == javaMathBigIntegerClass

private fun KClassifier?.isBigDecimal(): Boolean =
        this == BigDecimal::class || this == javaMathBigDecimalClass

private fun KClassifier?.isMap(): Boolean = this == Map::class

private fun KClassifier?.isList(): Boolean = this == List::class

private fun KClassifier?.isSet(): Boolean = this == Set::class

private fun KClassifier?.isGtv() = (this as? KClass<*>)?.isSubclassOf(Gtv::class) == true

private fun KClassifier?.isEnum(): Boolean = (this as? KClass<*>)?.java?.isEnum == true

private fun getEnumValue(classType: KType, enumValue: String, context: String): Any {
    @Suppress("UNCHECKED_CAST") val enum = (classType.classifier as? KClass<*>)?.java?.enumConstants as? Array<Enum<*>>
            ?: throw IllegalArgumentException("Invalid enum type ${classType.classifier}; context: $context")
    return enum.firstOrNull { it.name == enumValue }
            ?: throw IllegalArgumentException("invalid value '$enumValue' for enum ${classType.classifier}; context: $context")
}
