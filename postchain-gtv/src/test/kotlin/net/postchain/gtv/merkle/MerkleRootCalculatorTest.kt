// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv.merkle

import net.postchain.common.data.Hash
import net.postchain.common.toHex
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.merkleHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MerkleRootCalculatorTest {


    val calculator = MerkleHashCalculatorDummy()

    private val empty32bytesAsHex = "0101010101010101010101010101010101010101010101010101010101010101"
    private val expectedMerkleRootOf1 = "080303$empty32bytesAsHex"
    private val expectedMerkleRootOf4 = "0802040404050204060407"

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
    fun testGtvInteger() {
        var actual = GtvInteger(0L).merkleHash(GtvMerkleHashCalculator(Secp256K1CryptoSystem()))
        var expected = "90B136DFC51E08EE70ED929C620C0808D4230EC1015D46C92CCAA30772651DC0"
        assertEquals(expected, actual.toHex())

        actual = GtvInteger(1L).merkleHash(GtvMerkleHashCalculator(Secp256K1CryptoSystem()))
        expected = "6CCD14B5A877874DDC7CA52BD3AEDED5543B73A354779224BBB86B0FD315B418"
        assertEquals(expected, actual.toHex())

        actual = GtvInteger(127L).merkleHash(GtvMerkleHashCalculator(Secp256K1CryptoSystem()))
        expected = "EBA1A4FE3CDC6C5089D6222F00980599D5E943A933AD11BDEC942B08D1C8D419"
        assertEquals(expected, actual.toHex())

        actual = GtvInteger(128L).merkleHash(GtvMerkleHashCalculator(Secp256K1CryptoSystem()))
        expected = "CCC9C7E4A8FC166199E7708146EC6D043DCAD0A20266E064E802E5DD724A66DA"
        assertEquals(expected, actual.toHex())

        actual = GtvInteger(168L).merkleHash(GtvMerkleHashCalculator(Secp256K1CryptoSystem()))
        expected = "1DD1D428D59F66807F753FB3E307A65B1B57EACE358A4A94745AA049593A5AEE"
        assertEquals(expected, actual.toHex())

        actual = GtvInteger(255L).merkleHash(GtvMerkleHashCalculator(Secp256K1CryptoSystem()))
        expected = "7698DE397F332E1BCC03967CCC1196B0DACB86DC3700FC19566C4F3C322D599E"
        assertEquals(expected, actual.toHex())

        actual = GtvInteger(256L).merkleHash(GtvMerkleHashCalculator(Secp256K1CryptoSystem()))
        expected = "CA5F98D59E2E5FE04936A6CCF67F6BF8B5ABDF925BD0FE647A8718CBCE94BD9A"
        assertEquals(expected, actual.toHex())

        actual = GtvInteger(1023L).merkleHash(GtvMerkleHashCalculator(Secp256K1CryptoSystem()))
        expected = "57854DF20A828791922960583A1CE0328FE502DD7D9256852D864D492B3900A5"
        assertEquals(expected, actual.toHex())

        actual = GtvInteger(1024L).merkleHash(GtvMerkleHashCalculator(Secp256K1CryptoSystem()))
        expected = "4765E67F40EFD44127131C1347F389DFB3993D6AC211DDB04E2074E8C1639BD3"
        assertEquals(expected, actual.toHex())

        actual = GtvInteger(1256L).merkleHash(GtvMerkleHashCalculator(Secp256K1CryptoSystem()))
        expected = "0A336A98550BBC8182BE8DBA0517E0A6D0E49E2A598468A4B6FBF3AD53AC7BEA"
        assertEquals(expected, actual.toHex())

        actual = GtvInteger(32769L).merkleHash(GtvMerkleHashCalculator(Secp256K1CryptoSystem()))
        expected = "66892F0A7CF93E2FF0E8FE5BD475D933CE3B3E46195A36C959888F2F59AEB389"
        assertEquals(expected, actual.toHex())

        actual = GtvInteger(1234567890L).merkleHash(GtvMerkleHashCalculator(Secp256K1CryptoSystem()))
        expected = "91F23A381089997DF175AF0AE0DD3E44B651C255ABECA1683F15D831B59C236E"
        assertEquals(expected, actual.toHex())
    }
}