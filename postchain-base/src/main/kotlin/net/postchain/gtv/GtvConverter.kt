package net.postchain.gtv

import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
annotation class Name(val s: String)


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

}

fun parameterToValue(p: Parameter, gtv: Gtv?): Any? {
    if (gtv == null) return null
    if (p.parameterizedType is ParameterizedType) { // List types
        if (gtv !is GtvArray) throw IllegalArgumentException("Gtv must be array, but is ${gtv.type} with values $gtv")
        val listTypeArgument = (p.parameterizedType as ParameterizedType).actualTypeArguments[0]
        return parameterizedTypeArgumentToValue(listTypeArgument, gtv)
    }
    return classToValue(p.type, gtv)
}

fun parameterizedTypeArgumentToValue(t: Type, gtv: Gtv?): Any? {
    if (gtv == null) return null
    val gtvArray = gtv.asArray()
    if (t is WildcardType) {
        return gtvArray.map { parameterizedTypeArgumentToValue((t.upperBounds[0] as ParameterizedType).actualTypeArguments[0], it) }
    }
    if (t.typeName == "byte[]") return gtvArray.map { classToValue(ByteArray::class.java, it) }
    return gtvArray.map { classToValue(Class.forName(t.typeName), it) }
}

fun classToValue(c: Class<*>, gtv: Gtv?): Any? {
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

infix fun Class<*>.isLong(u: () -> Unit): Boolean {
    return this == Long::class.java || this == java.lang.Long::class.java
}

infix fun Class<*>.isString(u: () -> Unit): Boolean {
    return this == String::class.java || this == java.lang.String::class.java
}

infix fun Class<*>.isByteArray(u: () -> Unit): Boolean {
    return this == ByteArray::class.java
}
