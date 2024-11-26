// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv.merkle.proof

import net.postchain.gtv.GtvArray
import net.postchain.gtv.generateProof
import net.postchain.gtv.merkle.DictToGtvBinaryTreeHelper
import net.postchain.gtv.merkle.DictToGtvBinaryTreeHelper.expectedMerkleRoot1
import net.postchain.gtv.merkle.DictToGtvBinaryTreeHelper.expectedMerkleRoot4
import net.postchain.gtv.merkle.DictToGtvBinaryTreeHelper.expectedMerkleRootDictInDict
import net.postchain.gtv.merkle.MerkleHashCalculatorDummy
import net.postchain.gtv.merkle.PrintableTreeFactory
import net.postchain.gtv.merkle.TreeHelper
import net.postchain.gtv.merkle.TreePrinter
import net.postchain.gtv.merkle.path.GtvPath
import net.postchain.gtv.merkle.path.GtvPathFactory
import net.postchain.gtv.merkle.path.GtvPathSet
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * In this class we test if we can generate proofs out ofGtv dictionary structures.
 * 1. First we test to build a proof where the value-to-be-proved a primitive type value in the dict.
 * 2. Then we test to build a proof where the value-to-be-proved is a primitive value located in a sub-dict.
 * 3. Later we test to build a proof where the value-to-be-proved is a complex type (another dict)
 *
 * -----------------
 * How to read the tests
 * -----------------
 *
 * In the comments below
 *   "<...>" means "serialization" and
 *   "[ .. ]" means "hash" and
 *   "(a + b)" means append "b" after "a" into "ab"
 *
 * Since we are using the dummy hash function, all binary numbers will be +1 after hash function
 *  02 -> 03 etc.
 *
 * The dummy serializer doesn't do anything but converting an int to a byte:
 *   7 -> 07
 *   12 -> 0C
 *
 * -----------------
 * Note1: The testing of the exact serialization format is not very important and can be removed. The only important
 * thing is that we keep testing after deserialization.
 *
 * Note2: We are not testing the cache, so every test begins with a fresh Calculator (and therefore a fresh cache).
 */

class DictToMerkleProofTreeTest {

    private val ln = System.lineSeparator()
    private val proofFactory =GtvMerkleProofTreeFactory()

    // ---------------------
    // 1. First we test to build a proof where the value-to-be-proved a primitive type value in the dict.
    // ---------------------

    // -------------- Size 1 ------------

    @Test
    fun test_dict1_proof() {
        val calculator = MerkleHashCalculatorDummy()

        val path: Array<Any> = arrayOf("one")
        val gtvPath = GtvPathFactory.buildFromArrayOfPointers(path)
        val gtvPaths = GtvPathSet(setOf(gtvPath))
        val orgGtvDict = DictToGtvBinaryTreeHelper.buildGtvDictOf1()

        // How to convert one to hash?:
        // "one" ->(serialization) 6F6E65
        // 01 + [6F6E65] ->
        // 01706F66 ->
        val expectedTree =" +   $ln" +
                "/ \\ $ln" +
                "02706F66 *1 "

        val merkleProofTree = orgGtvDict.generateProof(gtvPaths, calculator)

        // Print the result tree
        val printer = TreePrinter()
        val pbt = PrintableTreeFactory.buildPrintableTreeFromProofTree(merkleProofTree)
        val resultPrintout = printer.printNode(pbt)
        //println(resultPrintout)

       assertEquals(expectedTree.trim(), resultPrintout.trim())

        // Make sure the merkle root stays the same as without proof
        val merkleProofRoot = merkleProofTree.merkleHash(calculator)
        assertEquals(expectedMerkleRoot1, TreeHelper.convertToHex(merkleProofRoot))

        // Proof -> Serialize
        val serialize: GtvArray = merkleProofTree.toGtv()
        //println("Serilalized: $serialize")

        val expectedSerialization = "[$ln" +
                "  104,$ln" + // 104 = dict head node type
                "  1,$ln" + // length of dict
                "  -10,$ln" + // no path elem
                "  [$ln" +
                "    100,$ln" + // 100 = hash
                "    x\"02706F66\"$ln" +
                "  ],$ln" +
                "  [$ln" +
                "    101,  $ln" + // 101 = value to prove
                "    \"one\", $ln" + // path elem = 1
                "    1$ln" +
                "  ]$ln" +
                "]$ln"

       assertEquals(TreeHelper.stripWhite(expectedSerialization), TreeHelper.stripWhite(serialize.toString())) // Not really needed, Can be removed

        // Serialize -> deserialize
        val deserialized = proofFactory.deserialize(serialize)

        // Print the result tree
        val pbtDes = PrintableTreeFactory.buildPrintableTreeFromProofTree(deserialized)
        val deserializedPrintout = printer.printNode(pbtDes)
        //println(deserializedPrintout)

       assertEquals(expectedTree.trim(), deserializedPrintout.trim())
    }


    // -------------- Size 4 ------------

    @Test
    fun test_dict4_proof() {
        val calculator = MerkleHashCalculatorDummy()

        val path: Array<Any> = arrayOf("four")
        val gtvPath = GtvPathFactory.buildFromArrayOfPointers(path)
        val gtvPaths = GtvPathSet(setOf(gtvPath))
        val orgGtvPath = DictToGtvBinaryTreeHelper.buildGtvDictOf4()

        // This is how the (dummy = +1) hash calculation works done for the right side of the path:
        //
        //
        // "four" serialized becomes bytes: 666F7572
        // "one" serialized becomes bytes: 6F6E65
        // "three" serialized becomes bytes: 7468726565
        // "two" serialized becomes bytes: 74776F
        //
        // 00 + [(00 +
        //         [
        //            (01 + [<three>]) +   <-- "th" is before "tw"
        //            (01 + [<3>])
        //         ])
        //       (00 +
        //         [
        //            (01 + [<two>]) +
        //            (01 + [<2>])
        // 00 + [(00 +
        //         [
        //            (01 + [7468726565]) +
        //            (01 + [03])
        //         ])
        //       (00 +
        //         [
        //            (01 + [74776F]) +
        //            (01 + [02])
        //         ])
        //         ])
        // 00 + [(00 + [017569736666 + 0104)] +
        //      [(00 + [01757870 + 0103)])
        // 00 + [(00 + 02766A746767 0205)] +
        //      [(00 + 02767971 0204])
        // 00 + 01 03776B756868 0306 +
        //      01 03777A72 0305
        // 000103776B75686803060103777A720305
        //
        val expectedTree =
                "       +               $ln" +
                        "      / \\       $ln" +
                        "     /   \\      $ln" +
                        "    /     \\     $ln" +
                        "   /       \\    $ln" +
                        "   +       010204776B75686804060204777A720405       $ln" +
                        "  / \\           $ln" +
                        " /   \\          $ln" +
                        " +   01037170670303   .   .   $ln" +
                        "/ \\             $ln" +
                        "0267707673 *4 - - - - - - "

        val merkleProofTree = orgGtvPath.generateProof(gtvPaths, calculator)

        // Print the result tree
        val printer = TreePrinter()
        val pbt = PrintableTreeFactory.buildPrintableTreeFromProofTree(merkleProofTree)
        val resultPrintout = printer.printNode(pbt)
        //println(resultPrintout)

       assertEquals(expectedTree.trim(), resultPrintout.trim())

        // Make sure the merkle root stays the same as without proof
        val merkleProofRoot = merkleProofTree.merkleHash(calculator)
        assertEquals(expectedMerkleRoot4, TreeHelper.convertToHex(merkleProofRoot))

        // Proof -> Serialize
        val serialize: GtvArray = merkleProofTree.toGtv()
        //println("Serilalized: $serialize")

        val expectedSerialization = "[$ln" +
                "  104, $ln" + // 104 = dict head node type
                "  4, $ln" + // length of the dict
                "  -10, $ln" + // no path elem
                "  [$ln" +
                "    102, $ln" + // 102 = dummy node
                "    [$ln" +
                "      102, $ln" + // 102 = dummy node
                "      [$ln" +
                "        100, $ln" + // 100 = hash
                "        x\"0267707673\"$ln" +
                "      ],$ln" +
                "      [$ln" +
                "        101,   $ln" + // 101 = value to prove
                "        \"four\", $ln" +  // path elem "four"
                "        4$ln" +
                "      ]$ln" +
                "    ],  $ln" +
                "    [$ln" +
                "      100,   $ln" + // 100 = hash
                "      x\"01037170670303\"$ln" +
                "    ]$ln" +
                "  ],   $ln" +
                "  [$ln" +
                "    100,   $ln" + // 100 = hash
                "    x\"010204776B75686804060204777A720405\"$ln" +
                "  ]$ln" +
                "]$ln"

       assertEquals(TreeHelper.stripWhite(expectedSerialization), TreeHelper.stripWhite(serialize.toString())) // Not really needed, Can be removed

        // Serialize -> deserialize
        val deserialized = proofFactory.deserialize(serialize)

        // Print the result tree
        val pbtDes = PrintableTreeFactory.buildPrintableTreeFromProofTree(deserialized)
        val deserializedPrintout = printer.printNode(pbtDes)
        //println(deserializedPrintout)

       assertEquals(expectedTree.trim(), deserializedPrintout.trim())

    }


    // ---------------------
    // 2. Then we test to build a proof where the value-to-be-proved is a primitive value located in a sub-dict.
    // ---------------------

    @Test
    fun test_dictOfDict_proof() {
        val calculator = MerkleHashCalculatorDummy()

        val path: Array<Any> = arrayOf("one", "seven")
        val gtvPath = GtvPathFactory.buildFromArrayOfPointers(path)
        val gtvPaths = GtvPathSet(setOf(gtvPath))
        val orgGtvDict = DictToGtvBinaryTreeHelper.buildGtvDictOf1WithSubDictOf2()

        val expectedTree = "       +               $ln" +
                "      / \\       $ln" +
                "     /   \\      $ln" +
                "    /     \\     $ln" +
                "   /       \\    $ln" +
                "   02706F66       *       $ln" +
                "          / \\   $ln" +
                "         /   \\  $ln" +
                " .   .   0103676B696A76030A   +   $ln" +
                "            / \\ $ln" +
                "- - - - - - 02746677666F *7 "

        val merkleProofTree = orgGtvDict.generateProof(gtvPaths, calculator)

        // Print the result tree
        val printer = TreePrinter()
        val pbt = PrintableTreeFactory.buildPrintableTreeFromProofTree(merkleProofTree)
        val resultPrintout = printer.printNode(pbt)
        //println(resultPrintout)

       assertEquals(expectedTree.trim(), resultPrintout.trim())

        // Make sure the merkle root stays the same as without proof
        val merkleProofRoot = merkleProofTree.merkleHash(calculator)
        assertEquals(expectedMerkleRootDictInDict, TreeHelper.convertToHex(merkleProofRoot))

        // Proof -> Serialize
        val serialize: GtvArray = merkleProofTree.toGtv()
        println("Serilalized: $serialize")

        val expectedSerialization =  "[$ln" +
                "  104, $ln" + // 104 = dict head node type
                "  1, $ln" + // length of the dict
                "  -10, $ln" + // no path elem
                "  [$ln" +
                "    100,$ln" +
                "    x\"02706F66\"$ln" +
                "  ],$ln" +
                "  [$ln" +
                "    104, $ln" + // 104 = dict head node type
                "    2, $ln" + // length of the dict
                "    \"one\", $ln" + // path elem "one"
                "    [$ln" +
                "      100, $ln" + // 100 = hash
                "      x\"0103676B696A76030A\"$ln" +
                "    ], $ln" +
                "    [$ln" +
                "      102, $ln" + // 102 = dummy node
                "      [$ln" +
                "        100, $ln" + // 100 = hash
                "        x\"02746677666F\"$ln" +
                "      ], $ln" +
                "      [$ln" +
                "        101, $ln" + // 101 = value to prove
                "        \"seven\", $ln" + // path elem "seven"
                "        7$ln" +
                "      ]$ln" +
                "    ]$ln" +
                "  ]$ln" +
                "]$ln"

       assertEquals(TreeHelper.stripWhite(expectedSerialization), TreeHelper.stripWhite(serialize.toString())) // Not really needed, Can be removed

        // Serialize -> deserialize
        val deserialized = proofFactory.deserialize(serialize)

        // Print the result tree
        val pbtDes = PrintableTreeFactory.buildPrintableTreeFromProofTree(deserialized)
        val deserializedPrintout = printer.printNode(pbtDes)
        println(deserializedPrintout)

       assertEquals(expectedTree.trim(), deserializedPrintout.trim())

    }

    // ---------------------
    // 3. Later we test to build a proof where the value-to-be-proved is a complex type (another dict)
    // ---------------------

    /**
     * This test will create a proof of a sub-dictionary inside the main dictionary.
     *
     * Note: This test depend on the auto-generated output of toString() of the "data class" of theGtv Dict.
     */
    @Test
    fun test_dictOfDict_proof_where_path_is_to_sub_dict() {
        val calculator = MerkleHashCalculatorDummy()

        val path: Array<Any> = arrayOf("one")
        val gtvPath: GtvPath = GtvPathFactory.buildFromArrayOfPointers(path)
        val gtvPaths = GtvPathSet(setOf(gtvPath))
        val orgGtvDict = DictToGtvBinaryTreeHelper.buildGtvDictOf1WithSubDictOf2()

        val expectedTree = " +   $ln" +
                "/ \\ $ln" +
                "02706F66 *[\"eight\": 8, \"seven\": 7] "
        
        val merkleProofTree = orgGtvDict.generateProof(gtvPaths, calculator)

        // Print the result tree
        val printer = TreePrinter()
        val pbt = PrintableTreeFactory.buildPrintableTreeFromProofTree(merkleProofTree)
        val resultPrintout = printer.printNode(pbt)
        //println(resultPrintout)

       assertEquals(expectedTree.trim(), resultPrintout.trim())

        // Make sure the merkle root stays the same as without proof
        val merkleProofRoot = merkleProofTree.merkleHash(calculator)
        assertEquals(expectedMerkleRootDictInDict, TreeHelper.convertToHex(merkleProofRoot))

        // Proof -> Serialize
        val serialize: GtvArray = merkleProofTree.toGtv()
        //println("Serilalized: $serialize")

        val expectedSerialization = "[$ln" +
                "  104,$ln" +  // 104 = dict head node type
                "  1,$ln" + // lenght of the dict
                "  -10,$ln" + // no path elem
                "  [$ln" +
                "    100,$ln" + // 100 = Hash
                "    x\"02706F66\"$ln" +
                "  ],$ln" +
                "  [$ln" +
                "    101, $ln" + // 101 = value to be proved (in this case an entire dict)
                "    \"one\", $ln" + // path elem "one"
                "    [$ln" +  // The value is a GtvDictionary, in it's raw form
                "      \"eight\":8,$ln" +
                "      \"seven\":7 $ln" +
                "    ]$ln" +
                "  ]$ln" +
                "]$ln"

       assertEquals(TreeHelper.stripWhite(expectedSerialization), TreeHelper.stripWhite(serialize.toString())) // Not really needed, Can be removed

        // Serialize -> deserialize
        val deserialized = proofFactory.deserialize(serialize)

        // Print the result tree
        val pbtDes = PrintableTreeFactory.buildPrintableTreeFromProofTree(deserialized)
        val deserializedPrintout = printer.printNode(pbtDes)
        //println(deserializedPrintout)

       assertEquals(expectedTree.trim(), deserializedPrintout.trim())
    }

}