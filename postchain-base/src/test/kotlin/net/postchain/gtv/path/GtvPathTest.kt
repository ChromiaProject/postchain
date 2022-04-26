// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv.path

import net.postchain.gtv.merkle.path.ArrayGtvPathElement
import net.postchain.gtv.merkle.path.GtvPath
import net.postchain.gtv.merkle.path.GtvPathFactory
import net.postchain.gtv.merkle.path.GtvPathLeafElement
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GtvPathTest {

    @Test
    fun testGtvPath_equals() {
        val keys1: Array<Any> = arrayOf(0, 7, "myKey")
        val keys2: Array<Any> = arrayOf(0, 7, "myKey")
        val path1 = GtvPathFactory.buildFromArrayOfPointers(keys1)
        val path2 = GtvPathFactory.buildFromArrayOfPointers(keys2)
       assertEquals(path1, path2)
    }

    @Test
    fun testGtvPath_notEquals() {
        val keys1: Array<Any> = arrayOf(0, 7, "myKey")
        val keys2: Array<Any> = arrayOf(0, 8, "myKey")
        val path1 = GtvPathFactory.buildFromArrayOfPointers(keys1)
        val path2 = GtvPathFactory.buildFromArrayOfPointers(keys2)
       assertNotEquals(path1, path2)
    }

    @Test
    fun testGtvPath_getTail() {

        val ints: Array<Any> = arrayOf(0,7)
        val org = GtvPathFactory.buildFromArrayOfPointers(ints)
       assertEquals(3, org.size())

        println("Path (size: ${org.size()} ) list: " + org.debugString())

        val firstElement  = firstArrayElement(org)
       assertEquals(0, firstElement.index)
        val tail1: GtvPath = GtvPath.getTailIfFirstElementIsArrayOfThisIndex(0, org)!!
       assertEquals(2, tail1.size())
        val tail1Fail: GtvPath? = GtvPath.getTailIfFirstElementIsArrayOfThisIndex(1, org) // Index = 1 won't find anything
       assertNull(tail1Fail)

        val secondElement = firstArrayElement(tail1)
       assertEquals(7, secondElement.index)
        val tail2: GtvPath = GtvPath.getTailIfFirstElementIsArrayOfThisIndex(7, tail1)!!
       assertEquals(1, tail2.size())

        val thirdElement = firstLeafElement(tail2)

    }

    fun firstArrayElement(gtvPath: GtvPath) : ArrayGtvPathElement {
       return gtvPath.pathElements[0] as ArrayGtvPathElement
    }
    fun firstLeafElement(gtvPath: GtvPath) : GtvPathLeafElement {
        return gtvPath.pathElements[0] as GtvPathLeafElement
    }
}