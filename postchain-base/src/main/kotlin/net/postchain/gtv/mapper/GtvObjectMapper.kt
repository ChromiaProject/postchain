package net.postchain.gtv.mapper

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDictionary
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.reflect.KClass

inline fun <reified T : Any> Gtv.toClass(): T {
    return GtvObjectMapper.fromGtv(this, T::class)
}

inline fun <reified T : Any> Gtv.toList(): List<T> {
    return GtvObjectMapper.fromArray(this, T::class)
}

object GtvObjectMapper {

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> fromArray(gtv: Gtv, classType: KClass<T>): List<T> {
        if (gtv !is GtvArray) throw IllegalArgumentException("Gtv must be dictionary type")
        return gtv.array.map {
            when {
                it is GtvDictionary -> fromGtv(it, classType)
                classType.typeParameters.isNotEmpty() -> throw IllegalArgumentException("Generics are not allowed")
                else -> classToValue(classType.java, it)
            }
        } as List<T>
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> fromGtv(gtv: Gtv, classType: KClass<T>): T {
        if (gtv !is GtvDictionary) throw IllegalArgumentException("Gtv must be dictionary type")
        if (classType.java.constructors.isEmpty()) throw IllegalArgumentException("Type $classType must have primary constructor")
        val constructor = classType.java.constructors[0]
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
    val gtvField = gtv[param.getAnnotation(Name::class.java)?.name!!]
    if (gtvField != null) return parameterToValue(param, gtvField)
    if (param.isAnnotationPresent(DefaultValue::class.java)) {
        val default = param.getAnnotation(DefaultValue::class.java)
        if (param.type isPrimitive {}) {
            return when {
                param.type isLong {} -> default.defaultLong
                param.type isString {} -> default.defaultString
                else -> default.defaultByteArray
            }
        }
        throw IllegalArgumentException("Default value not accepted for type: ${param.type}, must be Long, String or Bytearray")
    }
    if (param.isAnnotationPresent(Nullable::class.java)) {
        return null
    }
    throw IllegalArgumentException("Gtv is null, but neither default nor nullable annotation is present")
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
        classType isGtv {} -> gtv
        classType isLong {} -> gtv.asInteger()
        classType isString {} -> gtv.asString()
        classType isByteArray {} -> gtv.asByteArray()
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

private infix fun Class<*>.isPrimitive(u: () -> Unit): Boolean {
    return this isLong {} || this isString {} || this isByteArray {}
}

private infix fun Class<*>.isLong(u: () -> Unit): Boolean {
    return this == Long::class.java || this == java.lang.Long::class.java
}

private infix fun Class<*>.isString(u: () -> Unit): Boolean {
    return this == String::class.java || this == java.lang.String::class.java
}

private infix fun Class<*>.isByteArray(u: () -> Unit): Boolean {
    return this == ByteArray::class.java
}

private infix fun Class<*>.isGtv(u: () -> Unit) = this.isAssignableFrom(Gtv::class.java)
