package net.postchain.gtv

import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE_PARAMETER)
annotation class Name(val name: String)

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE_PARAMETER)
annotation class RawGtv

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE_PARAMETER)
annotation class Nullable

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE_PARAMETER)
annotation class DefaultValue(val defaultLong: Long = 0, val defaultString: String = "", val defaultByteArray: ByteArray = [])

inline fun <reified T : Any> GtvDictionary.toClass(): T {
    return GtvConverter.fromGtv(this, T::class)
}

inline fun <reified T : Any> GtvArray.toList(): List<T> {
    return GtvConverter.toList(this, T::class)
}

object GtvConverter {

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> toList(gtv: GtvArray, K: KClass<T>): List<T> {
        val v = gtv.array.map {
            when {
                it is GtvDictionary -> fromGtv(it, K)
                K.typeParameters.isNotEmpty() -> throw IllegalArgumentException("Generics are not allowed")
                else -> classToValue(K.java, it)
            }
        }
        return v as List<T>
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> fromGtv(g: GtvDictionary, K: KClass<T>): T {
        if (K.java.constructors.isEmpty()) throw IllegalArgumentException("Type must have primary constructor")
        val constructor = K.java.constructors[0]
        val v = constructor.parameters.map {
            annotatedParameterToValue(it, g)
        }

        return constructor.newInstance(*v.toTypedArray()) as T
    }

}

private fun annotatedParameterToValue(param: Parameter, gtv: GtvDictionary): Any? {
    return when {
        param.isAnnotationPresent(RawGtv::class.java) -> gtv
        param.isAnnotationPresent(Name::class.java) -> {
            val gtvField = gtv[param.getAnnotation(Name::class.java)?.name!!]
            if (gtvField == null) {
                if (param.isAnnotationPresent(DefaultValue::class.java)) {
                    val default =  param.getAnnotation(DefaultValue::class.java)
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
            parameterToValue(param, gtvField)
        }
        else -> {
            throw IllegalArgumentException("No annotation for parameter ${param.name} is present.")
        }
    }
}

private fun parameterToValue(p: Parameter, gtv: Gtv?): Any? {
    if (gtv == null) return null
    if (p.parameterizedType is ParameterizedType) { // List types
        if (gtv !is GtvArray) throw IllegalArgumentException("Gtv must be array, but is ${gtv.type} with values $gtv")
        val listTypeArgument = (p.parameterizedType as ParameterizedType).actualTypeArguments[0]
        return parameterizedTypeArgumentToValue(listTypeArgument, gtv)
    }
    return classToValue(p.type, gtv)
}

private fun parameterizedTypeArgumentToValue(t: Type, gtv: Gtv?): Any? {
    if (gtv == null) return null
    val gtvArray = gtv.asArray()
    if (t is WildcardType) {
        return gtvArray.map { parameterizedTypeArgumentToValue((t.upperBounds[0] as ParameterizedType).actualTypeArguments[0], it) }
    }
    if (t.typeName == "byte[]") return gtvArray.map { classToValue(ByteArray::class.java, it) }
    return gtvArray.map { classToValue(Class.forName(t.typeName), it) }
}

private fun classToValue(c: Class<*>, gtv: Gtv?): Any? {
    if (gtv == null) return null
    return when {
        c isLong {} -> gtv.asInteger()
        c isString {} -> gtv.asString()
        c isByteArray {} -> gtv.asByteArray()
        c isGtv {} -> gtv
        else -> {
            if (gtv !is GtvDictionary) throw IllegalArgumentException("Gtv must be a dictionary, but is: ${gtv.type} with values $gtv")
            val n = c.constructors[0].parameters.map {
                annotatedParameterToValue(it, gtv)
            }
            c.constructors[0].newInstance(*n.toTypedArray())
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
