// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base

import net.postchain.common.toHex
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

val cryptoSystem = Secp256K1CryptoSystem()
val calculator = GtvMerkleHashCalculator(cryptoSystem)

internal class MerkleTest {
    @Disabled
    @Test
    fun testMerkleRootOfSingleElement() {
        val merkleCalculation = merkleRoot(arrayOf("a"))
        val expected = stringToHash("a")
        assertEquals(expected.toHex(), merkleCalculation.toHex())
    }

    @Test
    fun testMerkleRootNoCollisions() {
        checkDifferent(arrayOf("a"), arrayOf("a", "a"))
        checkDifferent(arrayOf("a", "b", "c", "d", "e"), arrayOf("a", "b", "c", "d", "e", "e"))
        checkDifferent(arrayOf("a", "b", "c", "d", "e", "f"), arrayOf("a", "b", "c", "d", "e", "f", "e", "f"))
    }

    @Test
    fun testMerkleRootOrderSensitive() {
        checkDifferent(arrayOf("a", "b", "c", "d"), arrayOf("a", "c", "b", "d"))
    }

    private fun checkDifferent(list1: Array<String>, list2: Array<String>) {
        assertFalse(merkleRoot(list1).contentEquals(merkleRoot(list2)))
    }

    private fun merkleRoot(stringList: Array<String>): ByteArray {
        val hashList = hashList(stringList).toList()
        val gtvArr = GtvFactory.gtv(hashList.map { GtvFactory.gtv(it) })
        return gtvArr.merkleHash(calculator)
    }

    private fun hashList(stringList: Array<String>): Array<ByteArray> {
        return stringList.map { stringToHash(it) }.toTypedArray()
    }

    private fun stringToHash(string: String): ByteArray {
        return cryptoSystem.digest(string.toByteArray())
    }
}
