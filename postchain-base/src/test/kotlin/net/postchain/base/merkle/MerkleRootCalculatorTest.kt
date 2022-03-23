// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.base.merkle

import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.common.data.Hash
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.gtv.*
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkle.MerkleHashCalculatorDummy
import net.postchain.gtv.merkleHash
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MerkleRootCalculatorTest {


    val calculator = MerkleHashCalculatorDummy()

    private val empty32bytesAsHex = "0101010101010101010101010101010101010101010101010101010101010101"
    val expectedMerkleRootOf1 = "080303" + empty32bytesAsHex
    val expectedMerkleRootOf4 = "0802040404050204060407"

    @Test
    fun testStrArrayLength1_merkle_root() {
        val strArray = arrayOf("01")
        //val expectedTree = " +   \n" +
        //        "/ \\ \n" +
        //        "01 - "

        // Motivation for how the merkle root is calculated
        // ("07" is the prefix for the array head)
        // [07 + ([01 +01]) + 0000000000000000000000000000000000000000000000000000000000000000]
        // [07 + 0202       + 0000000000000000000000000000000000000000000000000000000000000000]
        // 08    0303         0101010101010101010101010101010101010101010101010101010101010101

        val listOfHashes: List<Hash> = strArray.map { TreeHelper.convertToByteArray(it) }
        val gtvArr = gtv(listOfHashes.map { gtv(it)})
        val merkleRoot = gtvArr.merkleHash(calculator)

       assertEquals(expectedMerkleRootOf1, TreeHelper.convertToHex(merkleRoot))
    }

    @Test
    fun testStrArrayLength4_merkle_root() {
        val strArray = arrayOf("01","02","03","04")
        //val expectedTree =
        //        "   +       \n" +
        //                "  / \\   \n" +
        //                " /   \\  \n" +
        //                " +   +   \n" +
        //                "/ \\ / \\ \n" +
        //                "01 02 03 04 \n"

        // Motivation for how the merkle root is calculated
        // ("07" is the prefix for the array head)
        // [07 +
        //     [00 + 0202 + 0203]
        //     +
        //     [00 + 0204 + 0205]
        //      ] ->
        // [07 +  0103030304 + 0103050306 ]
        // 08     0204040405   0204060407

        val listOfHashes: List<Hash> = strArray.map { TreeHelper.convertToByteArray(it) }
        val gtvArr = gtv(listOfHashes.map { gtv(it)})
        val merkleRoot = gtvArr.merkleHash(calculator)

       assertEquals(expectedMerkleRootOf4, TreeHelper.convertToHex(merkleRoot))
    }

    @Test
    fun testBlockHeaderData() {
        val blockHeaderRec = BlockHeaderData(
            GtvByteArray("EA7C89EC2886B4BB490233BAD968FA6B6D2E4432AF86D8E1DCE603E873AA1BBE".hexStringToByteArray()),
            GtvByteArray("1F3CA8300DC9AB8F7D81682411EBC81E299CF7C7FA35C12163980E0BA42A34FE".hexStringToByteArray()),
            GtvByteArray("E384EA79AD1CA544ADEE13C6B1CCD33497A00BBF6A1E2F6DC4C61E264E0C08B3".hexStringToByteArray()),
            GtvInteger(1618930736155L),
            GtvInteger(44L),
            GtvNull,
            GtvDictionary.build(mapOf(
                "l2RootEvent" to GtvByteArray("21521DD32F61FF94D8701F40620210DA9AA172102B19F71E50ED8189DE707402".hexStringToByteArray()),
                "l2RootState" to GtvByteArray("A96F3202DDEDB7F3228EEDA0F97AA39163E1EA1085E5FC52BA40836C429A8F71".hexStringToByteArray())
            )
        ))
        val blockRID = blockHeaderRec.toGtv().merkleHash(  GtvMerkleHashCalculator(SECP256K1CryptoSystem()) )
        println(GtvNull.merkleHash(GtvMerkleHashCalculator(SECP256K1CryptoSystem())).toHex())
        println(GtvByteArray("E384EA79AD1CA544ADEE13C6B1CCD33497A00BBF6A1E2F6DC4C61E264E0C08B3".hexStringToByteArray()).merkleHash(GtvMerkleHashCalculator(SECP256K1CryptoSystem())).toHex())
        print(blockRID.toHex())
    }
}