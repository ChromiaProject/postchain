// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv

import net.postchain.gtv.GtvFactory.gtv
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class GtvDictionaryTest {

    @Test
    fun testSortingOfKeys() {

        val myMap = hashMapOf(
                "x" to gtv(100),
                "ccc" to gtv(3),
                "1" to gtv(-1),
                "b" to gtv(2),
                "a" to gtv(1))

        val dict = GtvDictionary.build(myMap)
        val resList = mutableListOf<String>()
        dict.dict.forEach{
            resList.add(it.key)
        }

        val expected = listOf("1", "a", "b", "ccc", "x") // Sorted
        assertEquals(expected, resList.toList())

    }

}