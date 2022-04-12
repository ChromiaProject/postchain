package net.postchain.gtv

import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
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

fun classToValue(c: Class<*>, gtv: Gtv?): Any? {
    if (gtv == null) return null
    return when (c) {
        Long::class.java -> gtv.asInteger()
        java.lang.Long::class.java -> gtv.asInteger()
        String::class.java -> gtv.asString()
        java.lang.String::class.java -> gtv.asString()
        ByteArray::class.java -> gtv.asByteArray()
        else -> {
            val n = c.constructors[0].parameters.map {
                val g = gtv[it.getAnnotation(Name::class.java)?.s ?: it.name!!]
                parameterToValue(it, g)
            }
            c.constructors[0].newInstance(*n.toTypedArray())
        }
    }
}

fun parameterToValue(p: Parameter, gtv: Gtv?): Any? {
    if (gtv == null) return null
    if (p.type == List::class.java) {
        val listType = (p.parameterizedType as ParameterizedType).actualTypeArguments[0]
        if (listType.typeName == "byte[]") return gtv.asArray().map { classToValue(ByteArray::class.java, it) }
        if (listType is WildcardType) {
            val t = ((listType as WildcardType).upperBounds[0] as ParameterizedType).actualTypeArguments[0]
            return gtv.asArray().map { it.asArray().map { classToValue(Class.forName(t.typeName), it) } }
        }

        return gtv.asArray().map { classToValue(Class.forName(listType.typeName), it) }
    }
    return classToValue(p.type, gtv)

}

