package net.postchain.common.reflection

import assertk.assert
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TestClass(val value: Long)
class TestClassNoArg

internal class ClassloaderTest {

    @Test
    fun `Class is not found`() {
        val e = assertThrows<UserMistake> {
            constructorOf<TestClass>("nameNotMatchingClass")
        }
        assert(e.message!!).contains("nameNotMatchingClass not found")
    }

    @Test
    fun `Wrong constructor arguments`() {
        val e = assertThrows<ProgrammerMistake> {
            constructorOf<TestClass>("net.postchain.common.reflection.TestClass", String::class.java)
        }
        assert(e.message!!).isEqualTo("Constructor with arguments [java.lang.String] was not found in class net.postchain.common.reflection.TestClass")
    }

    @Test
    fun `New instance can be created for classes with default constructor`() {
        assert(newInstanceOf<TestClassNoArg>(TestClassNoArg::class.qualifiedName!!)).isInstanceOf(TestClassNoArg::class)
        assertThrows<ProgrammerMistake> {
            newInstanceOf<TestClass>(TestClass::class.qualifiedName!!)
        }
    }
}