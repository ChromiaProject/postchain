package net.postchain.gtv

import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.reflect.KClass

annotation class Name(val s: String)

annotation class RawGtv

annotation class Nullable

annotation class DefaultValue(val defaultLong: Long)


object GtvConverter {

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> fromGtv(g: GtvDictionary, K: KClass<T>): T {
        val constructor = K.java.constructors[0]
        val v = constructor.parameters.map {
            val gtv = g[it.getAnnotation(Name::class.java)?.s ?: it.name!!]
            parameterToValue(it, gtv)
        }

        return constructor.newInstance(*v.toTypedArray()) as T
    }

    @Suppress("UNCHECKED_CAST")
    fun <T: Any> toList(gtv: GtvArray, K: KClass<T>): List<T> {
        val v = gtv.array.map {
            when {
                it is GtvDictionary -> fromGtv(it, K)
                else -> classToValue(K.java, it)
            }
        }
        return v as List<T>
    }
}

inline fun <reified T: Any> GtvDictionary.toClass(): T {
    return GtvConverter.fromGtv(this, T::class)
}

inline fun <reified T: Any> GtvArray.toList(): List<T> {
    return GtvConverter.toList(this, T::class)
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
        else -> {
            if (gtv !is GtvDictionary) throw IllegalArgumentException("Gtv must be a dictionary, but is: ${gtv.type} with values $gtv")
            val n = c.constructors[0].parameters.map {
                val g = gtv[it.getAnnotation(Name::class.java)?.s ?: it.name!!]
                parameterToValue(it, g)
            }
            c.constructors[0].newInstance(*n.toTypedArray())
        }
    }
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
