// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv.merkle

import net.postchain.crypto.sha256Digest
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvString
import net.postchain.gtv.merkleHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GtvMerkleHashCalculatorV1Test {


    @Test
    fun testHashOfGtvCalculation_DummySerializaiton_DummyHash() {

        val calculator = MerkleHashCalculatorDummy()

        val iGtv = GtvInteger(7)
        // The "7" is expected to serialize to "07" (in hex)
        // The expected resulting leaf hash will be:
        val expectedResultAfterAddOneHash = "0208"
        // This expected result is two parts:
        // 1. "01" (= signals leaf) and
        // 2. the add-one-hashed value

        val result = calculator.calculateLeafHash(iGtv)
        assertEquals(expectedResultAfterAddOneHash, TreeHelper.convertToHex(result))
    }


    @Test
    fun testHashOfGtvCalculation_RealSerialization_RealHash() {

        val calculator = GtvMerkleHashCalculatorV1(::sha256Digest)

        val iGtv = GtvInteger(7)
        // The "7" is expected to serialize to "A303020107" (in hex)
        // The expected resulting leaf hash will be:
        val expectedResultAfterAddOneHash = "0140C7F79E11092AF89407F8F9A2A3230E3B92BE8398200AC00E8757BF1B9009"
        // This expected result is two parts:
        // 1. "01" (= signals leaf) and
        // 2. the 32 byte hashed value

        val result = calculator.calculateLeafHash(iGtv)
        assertEquals(expectedResultAfterAddOneHash, TreeHelper.convertToHex(result))
    }

    @Test
    fun testArrayNoCollisions() {
        val calculatorV1 = GtvMerkleHashCalculatorV1(::sha256Digest)
        val calculatorV2 = GtvMerkleHashCalculatorV2(::sha256Digest)
        val gtv1 = GtvArray(arrayOf(GtvString("a")))
        val gtv2 = GtvArray(arrayOf(GtvArray(arrayOf(GtvString("a")))))
        val gtv1HashV1 = gtv1.merkleHash(calculatorV1)
        val gtv2HashV1 = gtv2.merkleHash(calculatorV1)
        val gtv1HashV2 = gtv1.merkleHash(calculatorV2)
        val gtv2HashV2 = gtv2.merkleHash(calculatorV2)

        assertTrue(gtv1HashV1.contentEquals(gtv2HashV1))
        assertFalse(gtv1HashV2.contentEquals(gtv2HashV2))

        assertTrue(gtv1HashV1.contentEquals(gtv1HashV2))
        assertFalse(gtv2HashV1.contentEquals(gtv2HashV2))
    }
}