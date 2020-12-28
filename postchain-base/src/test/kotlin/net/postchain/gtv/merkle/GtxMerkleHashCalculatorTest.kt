// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv.merkle

import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.base.SECP256K1KeccakCryptoSystem
import net.postchain.base.merkle.TreeHelper
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvString
import net.postchain.gtv.merkleHash
import org.junit.Assert
import org.junit.Test

class GtvMerkleHashCalculatorTest {


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
        Assert.assertEquals(expectedResultAfterAddOneHash, TreeHelper.convertToHex(result))
    }


    @Test
    fun testHashOfGtvCalculation_RealSerialization_RealHash() {

        val calculator = GtvMerkleHashCalculator(SECP256K1CryptoSystem())

        val iGtv = GtvInteger(7)
        // The "7" is expected to serialize to "A303020107" (in hex)
        // The expected resulting leaf hash will be:
        val expectedResultAfterAddOneHash = "0140C7F79E11092AF89407F8F9A2A3230E3B92BE8398200AC00E8757BF1B9009"
        // This expected result is two parts:
        // 1. "01" (= signals leaf) and
        // 2. the 32 byte hashed value

        val result = calculator.calculateLeafHash(iGtv)
        Assert.assertEquals(expectedResultAfterAddOneHash, TreeHelper.convertToHex(result))
    }

    @Test
    fun testHashOfGtvCalculation_RealSerialization_RealHash_NoPrefix() {

        val calculator = GtvMerkleHashCalculator(SECP256K1CryptoSystem())

        val iGtv = GtvInteger(7)
        // The "7" is expected to serialize to "A303020107" (in hex)
        // The expected resulting leaf hash will be:
        val expectedResultAfterAddOneHash = "616B6CDFF1A56CF63A0F04151C409B15941D5816D50DC739DBB4795D7BB97B6E"
        // This expected result is two parts:
        // 1. "01" (= signals leaf) and
        // 2. the 32 byte hashed value

        val result = calculator.calculateLeafHash(iGtv, false)
        Assert.assertEquals(expectedResultAfterAddOneHash, TreeHelper.convertToHex(result))
    }

    @Test
    fun testHashOfGtvCalculation_RealSerialization_RealHash_NoPrefix_Keccak() {

        val calculator = GtvMerkleHashCalculator(SECP256K1KeccakCryptoSystem())

        val iGtv = GtvInteger(7)
        // The "7" is expected to serialize to "A303020107" (in hex)
        // The expected resulting leaf hash will be:
        val expectedResultAfterAddOneHash = "50EACF1E3D924DAFF22AEE73C3F61FA6D9AA489857B0B9C44AE460E7DA0EC635"
        // This expected result is two parts:
        // 1. "01" (= signals leaf) and
        // 2. the 32 byte hashed value

        val result = calculator.calculateLeafHash(iGtv, false)
        Assert.assertEquals(expectedResultAfterAddOneHash, TreeHelper.convertToHex(result))
    }

    @Test
    fun testHashOfGtvCalculation_RealSerialization_RealHash2() {

        val calculator =GtvMerkleHashCalculator(SECP256K1CryptoSystem())

        val aGtv = GtvByteArray(GtvInteger(1).merkleHash(calculator))
        println(TreeHelper.convertToHex(GtvInteger(1).merkleHash(calculator)))
        // The "7" is expected to serialize to "A303020107" (in hex)
        // The expected resulting leaf hash will be:
        val expectedResultAfterAddOneHash = "1A08D303DD6AD0745235CA2F629C26D07F6E6A58E65FCB40A421DFC1549E28C9"
        // This expected result is two parts:
        // 1. "01" (= signals leaf) and
        // 2. the 32 byte hashed value

        val result = calculator.calculateLeafHash(aGtv)
        Assert.assertEquals(expectedResultAfterAddOneHash, TreeHelper.convertToHex(result))
    }

    @Test
    fun testHashOfGtvCalculation_RealSerialization_RealHash2_NoPrefix() {

        val calculator =GtvMerkleHashCalculator(SECP256K1CryptoSystem())

        val aGtv = GtvByteArray(GtvInteger(1).merkleHash(calculator, false))
        println(TreeHelper.convertToHex(GtvInteger(1).merkleHash(calculator, false)))
        // The "7" is expected to serialize to "A303020107" (in hex)
        // The expected resulting leaf hash will be:
        val expectedResultAfterAddOneHash = "534A9A39D6FBBD402488E789F94AED22A05E23869AA576FCD2D0EBB2A0FE6CBD"
        // This expected result is two parts:
        // 1. "01" (= signals leaf) and
        // 2. the 32 byte hashed value

        val result = calculator.calculateLeafHash(aGtv, false)
        Assert.assertEquals(expectedResultAfterAddOneHash, TreeHelper.convertToHex(result))
    }

    @Test
    fun testHashOfGtvCalculation_RealSerialization_RealHash3() {

        val calculator =GtvMerkleHashCalculator(SECP256K1CryptoSystem())

        val sGtv = GtvString("l2RootHash")
//        println(TreeHelper.convertToHex(GtvInteger(1).merkleHash(calculator)))
        // The "7" is expected to serialize to "A303020107" (in hex)
        // The expected resulting leaf hash will be:
        val expectedResultAfterAddOneHash = "43758C97091F5141260E8E3FD3A352A8FE106C353FCC7C9CDEEC71CEEFFDBB0F"
        // This expected result is two parts:
        // 1. "01" (= signals leaf) and
        // 2. the 32 byte hashed value

        val result = calculator.calculateLeafHash(sGtv)
        Assert.assertEquals(expectedResultAfterAddOneHash, TreeHelper.convertToHex(result))
    }

    @Test
    fun testHashOfGtvCalculation_RealSerialization_RealHash4() {

        val calculator = GtvMerkleHashCalculator(SECP256K1CryptoSystem())

        val sGtv = GtvByteArray(GtvInteger(3).merkleHash(calculator).plus(GtvInteger(4).merkleHash(calculator)))
        println(TreeHelper.convertToHex(GtvInteger(3).merkleHash(calculator).plus(GtvInteger(4).merkleHash(calculator))))
        // The "7" is expected to serialize to "A303020107" (in hex)
        // The expected resulting leaf hash will be:
        val expectedResultAfterAddOneHash = "760D9AD3B042B5627422D5528D7FC327BA288DA7754E5CBED0C0E3F4D0B0FCB4"
        // This expected result is two parts:
        // 1. "01" (= signals leaf) and
        // 2. the 32 byte hashed value

        val result = calculator.calculateLeafHash(sGtv)
        Assert.assertEquals(expectedResultAfterAddOneHash, TreeHelper.convertToHex(result))
    }

    @Test
    fun testHashOfGtvCalculation_RealSerialization_RealHash4_NoPrefix() {

        val calculator = GtvMerkleHashCalculator(SECP256K1CryptoSystem())

        val sGtv = GtvByteArray(GtvInteger(3).merkleHash(calculator, false).plus(GtvInteger(4).merkleHash(calculator, false)))
        println(TreeHelper.convertToHex(GtvInteger(3).merkleHash(calculator, false).plus(GtvInteger(4).merkleHash(calculator, false))))
        // The "7" is expected to serialize to "A303020107" (in hex)
        // The expected resulting leaf hash will be:
        val expectedResultAfterAddOneHash = "548A908259BE67AAE86F4A49E41D8DF7B08AEF79470EBDCD22928C15B15A6FBF"
        // This expected result is two parts:
        // 1. "01" (= signals leaf) and
        // 2. the 32 byte hashed value

        val result = calculator.calculateLeafHash(sGtv, false)
        Assert.assertEquals(expectedResultAfterAddOneHash, TreeHelper.convertToHex(result))
    }

    @Test
    fun testHashOfGtvCalculation_RealSerialization_RealHash4_NoPrefix_Keccak() {

        val calculator = GtvMerkleHashCalculator(SECP256K1KeccakCryptoSystem())

        val sGtv = GtvByteArray(GtvInteger(3).merkleHash(calculator, false).plus(GtvInteger(4).merkleHash(calculator, false)))
        println(TreeHelper.convertToHex(GtvInteger(3).merkleHash(calculator, false).plus(GtvInteger(4).merkleHash(calculator, false))))
        // The "7" is expected to serialize to "A303020107" (in hex)
        // The expected resulting leaf hash will be:
        val expectedResultAfterAddOneHash = "391A05A0A526CB4252B06F8E02763D07290D5A3CB0BA1FE34388EBBF7D3E8BE2"
        // This expected result is two parts:
        // 1. "01" (= signals leaf) and
        // 2. the 32 byte hashed value

        val result = calculator.calculateLeafHash(sGtv, false)
        Assert.assertEquals(expectedResultAfterAddOneHash, TreeHelper.convertToHex(result))
    }
}