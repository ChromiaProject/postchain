package net.postchain.common.reflection

import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import java.lang.reflect.Constructor

@Suppress("UNCHECKED_CAST")
inline fun <reified T> constructorOf(className: String, vararg constructorArgs: Class<*>): Constructor<out T> {
    return try {
        Class.forName(className).getConstructor(*constructorArgs) as Constructor<out T>
    } catch (e: ClassNotFoundException) {
        throw UserMistake("Configured class $className not found")
    } catch (e: NoSuchMethodException) {
        throw ProgrammerMistake("Constructor with arguments ${constructorArgs.map { it.name }} was not found in class $className")
    }
}

inline fun <reified T> newInstanceOf(className: String): T {
    return constructorOf<T>(className).newInstance()
}
