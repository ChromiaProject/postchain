// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv.path

import net.postchain.gtv.merkle.path.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GtvPathSetTest {

    val dummyPrevElem = ArrayGtvPathElement(null, 99)

    @Test
    fun testGtvPathSet_3path_Scenario() {

        // ------------ Path 1 ---------------
        val ints: Array<Any> = arrayOf(0,7)
        val path1 = GtvPathFactory.buildFromArrayOfPointers(ints)
       assertEquals(3, path1.size())

        println("Path (size: ${path1.size()} ) list: " + path1.debugString())

        // ------------ Path 2 ---------------
        val mix: Array<Any> = arrayOf(3, "myKey", 2)
        val path2 = GtvPathFactory.buildFromArrayOfPointers(mix)
       assertEquals(4, path2.size())

        println("Path (size: ${path2.size()} ) list: " + path2.debugString())

        // ------------ Path 3 (actually invalid IMO) ---------------
        val mixInvalid: Array<Any> = arrayOf(3, "myKey", 2, 5)
        // TODO: Could be prevented in PathSet constructor, but it seems to be an expensive check. Other ideas?
        // The reason Olle thinks thi is invalid is that path2 signals a leaf at position 2, but path 3 claims that
        // we want to prove a sub part of 2. This is not needed since we are proving the entire 2 anyway.
        val path3 = GtvPathFactory.buildFromArrayOfPointers(mixInvalid)
       assertEquals(5, path3.size())

        println("Path (size: ${path3.size()} ) list: " + path3.debugString())

        // ------------ Build the set --------------
        val paths = GtvPathSet(setOf(path1, path2, path3))
       assertEquals(3, paths.paths.size)

        // ------------ Dig down and extract --------------
        val elem = paths.getPathLeafOrElseAnyCurrentPathElement()
        println ("Found elem: $elem")
       assertTrue(elem != null) // According to the definition we don't know which one we will find (since there are two)

        // ---- 0. The short path ---
        val pathsWithIndex0 = paths.getTailIfFirstElementIsArrayOfThisIndexFromList(0)
       assertEquals(1, pathsWithIndex0.paths.size)
        val elemIndex0 = pathsWithIndex0.getPathLeafOrElseAnyCurrentPathElement()
       assertEquals(ArrayGtvPathElement(null, 7),  elemIndex0)

        // ---- 1. Dead end ---
        val pathsWithIndex1 = paths.getTailIfFirstElementIsArrayOfThisIndexFromList(1)
       assertEquals(0, pathsWithIndex1.paths.size)
        val elemIndex1 = pathsWithIndex1.getPathLeafOrElseAnyCurrentPathElement()
       assertEquals(null,  elemIndex1) // This is ok, we just return "null" if there is no element

        // ---- 3. The long path ---
        val pathsWithIndex3 = paths.getTailIfFirstElementIsArrayOfThisIndexFromList(3)
       assertEquals(2, pathsWithIndex3.paths.size)
        val elemIndex3 = pathsWithIndex3.getPathLeafOrElseAnyCurrentPathElement()
       assertEquals(DictGtvPathElement(null, "myKey"),  elemIndex3)

        val pathWithIndexMyKey = pathsWithIndex3.getTailIfFirstElementIsDictOfThisKeyFromList("myKey")
       assertEquals(2, pathWithIndexMyKey.paths.size)
        val elemIndex3myKey = pathWithIndexMyKey.getPathLeafOrElseAnyCurrentPathElement()
       assertEquals(ArrayGtvPathElement(null, 2),  elemIndex3myKey)

        // ---- Look for First leaf ---
        val myKeyPathsWithIndex2 = pathWithIndexMyKey.getTailIfFirstElementIsArrayOfThisIndexFromList(2)
       assertEquals(2, myKeyPathsWithIndex2.paths.size)
        val elemIndex3myKeyLeaf = myKeyPathsWithIndex2.getPathLeafOrElseAnyCurrentPathElement()
       assertEquals(GtvPathLeafElement(dummyPrevElem), elemIndex3myKeyLeaf) // Even though there are two possible elements, one is a leaf and that one must be returned.

        // ---- Look for Second leaf ---
        val myKeyPathsWithIndex2And5 = myKeyPathsWithIndex2.getTailIfFirstElementIsArrayOfThisIndexFromList(5)
       assertEquals(1, myKeyPathsWithIndex2And5.paths.size)
        val elemIndex3myKey2Leaf = myKeyPathsWithIndex2And5.getPathLeafOrElseAnyCurrentPathElement()
       assertEquals(GtvPathLeafElement(dummyPrevElem), elemIndex3myKey2Leaf) // One leaf left, should be a trivial check

    }
}