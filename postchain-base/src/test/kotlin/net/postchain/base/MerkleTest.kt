// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

val cryptoSystem = Secp256K1CryptoSystem()
val calculator = GtvMerkleHashCalculator(cryptoSystem)

class MerkleTest {


    fun stringToHash(string: String): ByteArray {
        return cryptoSystem.digest(string.toByteArray())
    }

    fun hashList(stringList: Array<String>): Array<ByteArray> {
        return stringList.map({stringToHash(it)}).toTypedArray()
    }

    fun merkleRoot(stringList: Array<String>): ByteArray {
        val hashList = hashList(stringList).toList()
        val gtvArr = GtvFactory.gtv(hashList.map { GtvFactory.gtv(it) })
        return gtvArr.merkleHash(calculator)

        //return computeMerkleRootHash(cryptoSystem, hashList(stringList))
    }

    fun checkDifferent(list1: Array<String>, list2: Array<String>) {
        val root1 = merkleRoot(list1)
        val root2 = merkleRoot(list2)
        assertByteArrayNotEqual(root1, root2)
    }
    val a = arrayOf("a")
    val aa = arrayOf("a", "a")
    val abcde = arrayOf("a", "b", "c", "d", "e")
    val abcdee= arrayOf("a", "b", "c", "d", "e", "e")
    val abcdef = arrayOf("a", "b", "c", "d", "e", "f")
    val abcdefef = arrayOf("a", "b", "c", "d", "e", "f", "e", "f")

    fun assertByteArrayEqual(expected: ByteArray, actual: ByteArray) {
        assertTrue(expected.contentEquals(actual))
    }

    fun assertByteArrayNotEqual(val1: ByteArray, val2: ByteArray) {
        assertFalse(val1.contentEquals(val2))
    }

    /*
    @Test
    fun testMerkleRootOfEmptyListIs32Zeroes() {
        assertByteArrayEqual(kotlin.ByteArray(32), merkleRoot(emptyArray()))
    }

    @Test
    fun testMerkleRootOfSingleElement() {
        val merkleCalculation = merkleRoot(a)
        println("merkle: " + convertToHex(merkleCalculation))
        val expected =stringToHash("a")
        println("expected: " + convertToHex(expected))
        assertByteArrayEqual(expected, merkleCalculation)
    }

    @Test
    fun testMerkleRootNoCollisions() {
        checkDifferent(a, aa)
        checkDifferent(abcde, abcdee)
        checkDifferent(abcdef, abcdefef)
    }
    */

}