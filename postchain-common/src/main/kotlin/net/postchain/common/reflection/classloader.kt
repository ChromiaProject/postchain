package net.postchain.common.reflection

import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import java.lang.reflect.Constructor

/**
 * Gets a constructor matching the supplied constructor arguments from the global classloader
 *
 * @param className The fully qualified name of the class
 * @param constructorArgs references to the types of the constructor arguments
 */
@Suppress("UNCHECKED_CAST")
fun <T> constructorOf(className: String, vararg constructorArgs: Class<*>): Constructor<out T> {
    return try {
        Class.forName(className).getConstructor(*constructorArgs) as Constructor<out T>
    } catch (e: ClassNotFoundException) {
        throw UserMistake("Configured class $className not found")
    } catch (e: NoSuchMethodException) {
        throw ProgrammerMistake("Constructor with arguments ${constructorArgs.map { it.typeName }} was not found in class $className")
    }
}

/**
 * Creates a new instance via the default constructor from the global classloader
 *
 * @param className The fully qualified name of the class to construct
 */
inline fun <reified T> newInstanceOf(className: String): T {
    return constructorOf<T>(className).newInstance()
}
