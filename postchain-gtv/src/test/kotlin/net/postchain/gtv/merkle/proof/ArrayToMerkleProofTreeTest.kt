// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv.merkle.proof

import net.postchain.gtv.GtvArray
import net.postchain.gtv.generateProof
import net.postchain.gtv.merkle.ArrayToGtvBinaryTreeHelper
import net.postchain.gtv.merkle.ArrayToGtvBinaryTreeHelper.expected1ElementArrayMerkleRoot
import net.postchain.gtv.merkle.ArrayToGtvBinaryTreeHelper.expected4ElementArrayMerkleRoot
import net.postchain.gtv.merkle.ArrayToGtvBinaryTreeHelper.expected7ElementArrayMerkleRoot
import net.postchain.gtv.merkle.ArrayToGtvBinaryTreeHelper.expectet7and3ElementArrayMerkleRoot
import net.postchain.gtv.merkle.MerkleHashCalculatorDummy
import net.postchain.gtv.merkle.PrintableTreeFactory
import net.postchain.gtv.merkle.TreeHelper
import net.postchain.gtv.merkle.TreeHelper.stripWhite
import net.postchain.gtv.merkle.TreePrinter
import net.postchain.gtv.merkle.path.GtvPath
import net.postchain.gtv.merkle.path.GtvPathFactory
import net.postchain.gtv.merkle.path.GtvPathSet
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * In this class we test if we can generate proofs out ofGtv array structures.
 * 1. First we test to build a proof where the value-to-be-proved a primitive type value in the array.
 * 2. Then we create a double proof (more than one value-to-be-proved in the same proof tree).
 * 3. Then we test to build a proof where the value-to-be-proved is a primitive value located in a sub-array.
 * 4. Later we test to build a proof where the value-to-be-proved is a complex type (another array)
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

class ArrayToMerkleProofTreeTest {

    private val ln = System.lineSeparator()
    private val proofFactory =GtvMerkleProofTreeFactory()

    // ---------------------
    // 1. First we test to build a proof where the value-to-be-proved a primitive type value in the array.
    // ---------------------
    // -------------- Size 1 ------------

    @Test
    fun test_ArrOf1_proof() {
        val calculator = MerkleHashCalculatorDummy()

        val path: Array<Any> = arrayOf(0)
        val gtvPath: GtvPath = GtvPathFactory.buildFromArrayOfPointers(path)
        val gtvPaths = GtvPathSet(setOf(gtvPath))
        val orgGtvArr = ArrayToGtvBinaryTreeHelper.buildGtvArrayOf1()

        val expectedTree =
                " +   $ln" +
                "/ \\ $ln" +
                "*1 0000000000000000000000000000000000000000000000000000000000000000"

        val merkleProofTree = orgGtvArr.generateProof(gtvPaths, calculator)

        // Print the result tree
        val printer = TreePrinter()
        val pbt = PrintableTreeFactory.buildPrintableTreeFromProofTree(merkleProofTree)
        val resultPrintout = printer.printNode(pbt)
        //println(resultPrintout)

        assertEquals(expectedTree.trim(), resultPrintout.trim())

        // Make sure the merkle root stays the same as without proof
        val merkleProofRoot = merkleProofTree.merkleHash(calculator)
        assertEquals(expected1ElementArrayMerkleRoot, TreeHelper.convertToHex(merkleProofRoot))

        // Proof -> Serialize
        val serialize: GtvArray = merkleProofTree.toGtv()
        //println("Serialized: $serialize")

        val expectedSerialization = "[$ln" +
                "  103,$ln" +  // 103 =  node type is array
                "  1,$ln" +  // lenght of array
                "  -10,$ln" + // (no path/position given)
                "  [$ln" +
                "    101,$ln" + // 101 = value to prove
                "    0,$ln" + //path/position = 0
                "    1$ln" + // Actual value
                "  ],$ln" +
                "  [$ln" +
                "    100,$ln" + // 100 = hash
                "    x\"0000000000000000000000000000000000000000000000000000000000000000\"$ln" +
                "  ]$ln" +
                "]$ln"

       assertEquals(stripWhite(expectedSerialization), stripWhite(serialize.toString())) // Not really needed, Can be removed

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
    fun test_Arrof4_proof() {
        val calculator = MerkleHashCalculatorDummy()

        val path: Array<Any> = arrayOf(0)
        val gtvPath = GtvPathFactory.buildFromArrayOfPointers(path)
        val gtvPaths = GtvPathSet(setOf(gtvPath))
        val orgGtvArr = ArrayToGtvBinaryTreeHelper.buildGtvArrayOf4()

        // This is how the (dummy = +1) hash calculation works done for the right side of the path:
        //
        // 00 + [(01 + [03]) + (01 + [04])]
        // 00 + [(01 + 04) + (01 + 05)]
        // 00 + [0104 + 0105] <-- Now we have the hash of the leaf "3" (=0104) and leaf "4" (0105).
        // 00 + [01040105]
        // 00 + 02050206
        // 0002050206  <-- Combined hash of 3 and 4.

        val expectedTree =
                "   +       $ln" +
                "  / \\   $ln" +
                " /   \\  $ln" +
                " +   0103050306   $ln" +
                "/ \\     $ln" +
                "*1 0203 - - "

        val merkleProofTree = orgGtvArr.generateProof(gtvPaths, calculator)

        // Print the result tree
        val printer = TreePrinter()
        val pbt = PrintableTreeFactory.buildPrintableTreeFromProofTree(merkleProofTree)
        val resultPrintout = printer.printNode(pbt)
        //println(resultPrintout)

       assertEquals(expectedTree.trim(), resultPrintout.trim())

        // Make sure the merkle root stays the same as without proof
        val merkleProofRoot = merkleProofTree.merkleHash(calculator)
        assertEquals(expected4ElementArrayMerkleRoot, TreeHelper.convertToHex(merkleProofRoot))

        // Proof -> Serialize
        val serialize: GtvArray = merkleProofTree.toGtv()
        //println("Serilalized: $serialize")

        val expectedSerialization = "[$ln" +
                "  103, $ln" +// 103 = array head node type
                "  4, $ln" + // length of array
                "  -10, $ln" + // no path elem
                "  [$ln" +
                "    102, $ln" +
                "    [$ln" +
                "      101, $ln" +// 101 = value to prove
                "      0, $ln" + // path elem = 0
                "      1$ln" +
                "    ], $ln" +
                "    [$ln" +
                "      100, $ln" +// 100 = hash
                "      x\"0203\"$ln" +
                "    ]$ln" +
                "  ], $ln" +
                "  [$ln" +
                "    100, $ln" +// 100 = hash
                "    x\"0103050306\"$ln" +
                "  ]$ln" +
                "]$ln"

       assertEquals(stripWhite(expectedSerialization), stripWhite(serialize.toString())) // Not really needed, Can be removed

        // Serialize -> deserialize
        val deserialized = proofFactory.deserialize(serialize)


        // Print the result tree
        val pbtDes = PrintableTreeFactory.buildPrintableTreeFromProofTree(deserialized)
        val deserializedPrintout = printer.printNode(pbtDes)
        //println(deserializedPrintout)

       assertEquals(expectedTree.trim(), deserializedPrintout.trim())

    }

    // -------------- Size 7 ------------

    @Test
    fun test_ArrOf7_proof() {
        val calculator = MerkleHashCalculatorDummy()

        val path: Array<Any> = arrayOf(3)
        val gtvPath: GtvPath = GtvPathFactory.buildFromArrayOfPointers(path)
        val gtvPaths = GtvPathSet(setOf(gtvPath))
        val orgGtvArr = ArrayToGtvBinaryTreeHelper.buildGtvArrayOf7()

        // This is how the (dummy = +1) hash calculation works done for the right side of the path:
        //
        // 00 + [
        //        (00 + [(01 + [05]) +
        //               (01 + [06])])
        //        +
        //        (01 + [07])
        //      ]
        // 00 + [(00 + [0106 + 0107]) + 0108 ]
        // 00 + [00 + 02070208 + 0108 ]
        // 00 + [00020702080108 ]
        // 00 +  01030803090209
        val expectedTree = "       +               $ln" +
                "      / \\       $ln" +
                "     /   \\      $ln" +
                "    /     \\     $ln" +
                "   /       \\    $ln" +
                "   +       0102040804090309       $ln" +
                "  / \\           $ln" +
                " /   \\          $ln" +
                " 0103030304   +   .   .   $ln" +
                "    / \\         $ln" +
                "- - 0204 *4 - - - - "

        val merkleProofTree:GtvMerkleProofTree = orgGtvArr.generateProof(gtvPaths, calculator)

        // Print the result tree
        val printer = TreePrinter()
        val pbt = PrintableTreeFactory.buildPrintableTreeFromProofTree(merkleProofTree)
        val resultPrintout = printer.printNode(pbt)
        //println(resultPrintout)

       assertEquals(expectedTree.trim(), resultPrintout.trim())

        // Make sure the merkle root stays the same as without proof
        val merkleProofRoot = merkleProofTree.merkleHash(calculator)
        assertEquals(expected7ElementArrayMerkleRoot, TreeHelper.convertToHex(merkleProofRoot))

        // Proof -> Serialize
        val serialize: GtvArray = merkleProofTree.toGtv()
        //println("Serilalized: $serialize")

        val expectedSerialization = "[$ln" +
                "  103,$ln" + // 103 = array head node type
                "  7,$ln" + // length of array
                "  -10,$ln" + // no path elem
                "  [$ln" +
                "    102,$ln" + // 102 = dummy node
                "      [$ln" +
                "        100,$ln" + // 100 = hash
                "        x\"0103030304\"$ln" +
                "      ],$ln" +
                "      [$ln" +
                "        102,$ln" + // 102 = dummy node
                "        [$ln" +
                "          100,$ln" +  // 100 = hash
                "          x\"0204\"$ln" +
                "        ],$ln" +
                "        [$ln" +
                "          101,$ln" + // 101 = value to prove
                "          3,$ln" + // path elem = 3
                "          4$ln" +
                "        ]$ln" +
                "      ]$ln" +
                "    ],$ln" +
                "    [$ln" +
                "      100,$ln" + // 100 = hash
                "      x\"0102040804090309\"$ln" +
                "    ]$ln" +
                "  ]$ln"

       assertEquals(stripWhite(expectedSerialization), stripWhite(serialize.toString())) // Not really needed, Can be removed

        // Serialize -> deserialize
        val deserialized = proofFactory.deserialize(serialize)

        // Print the result tree
        val pbtDes = PrintableTreeFactory.buildPrintableTreeFromProofTree(deserialized)
        val deserializedPrintout = printer.printNode(pbtDes)
        //println(deserializedPrintout)

       assertEquals(expectedTree.trim(), deserializedPrintout.trim())
    }

    // ---------------------
    // 2. Then we create a double proof (more than one value-to-be-proved in the same proof tree).
    // ---------------------

    @Test
    fun test_tree_of7_with_double_proof() {
        val calculator = MerkleHashCalculatorDummy()

        val path1: Array<Any> = arrayOf(3)
        val path2: Array<Any> = arrayOf(6)
        val gtvPath1: GtvPath = GtvPathFactory.buildFromArrayOfPointers(path1)
        val gtvPath2: GtvPath = GtvPathFactory.buildFromArrayOfPointers(path2)
        val gtvPaths = GtvPathSet(setOf(gtvPath1, gtvPath2))
        val orgGtvArr = ArrayToGtvBinaryTreeHelper.buildGtvArrayOf7()

        val expectedTree =
                "       +               $ln" +
                "      / \\       $ln" +
                "     /   \\      $ln" +
                "    /     \\     $ln" +
                "   /       \\    $ln" +
                "   +       +       $ln" +
                "  / \\     / \\   $ln" +
                " /   \\   /   \\  $ln" +
                " 0103030304   +   0103070308   *7   $ln" +
                "    / \\         $ln" +
                "- - 0204 *4 - - - - "

        val merkleProofTree = orgGtvArr.generateProof(gtvPaths, calculator)

        // Print the result tree
        val printer = TreePrinter()
        val pbt = PrintableTreeFactory.buildPrintableTreeFromProofTree(merkleProofTree)
        val resultPrintout = printer.printNode(pbt)
        //println(resultPrintout)

       assertEquals(expectedTree.trim(), resultPrintout.trim())

        // Make sure the merkle root stays the same as without proof
        val merkleProofRoot = merkleProofTree.merkleHash(calculator)
        assertEquals(expected7ElementArrayMerkleRoot, TreeHelper.convertToHex(merkleProofRoot))
    }

    // ---------------------
    // 3. Then we test to build a proof where the value-to-be-proved is a primitive value located in a sub-array.
    // ---------------------
    // -------------- Size 7 with inner 3 ------------

    @Test
    fun test_ArrayLength7_withInnerLength3Array_path2nine() {
        val calculator = MerkleHashCalculatorDummy()

        val path: Array<Any> = arrayOf(3,1)
        val gtvPath = GtvPathFactory.buildFromArrayOfPointers(path)
        val gtvPaths = GtvPathSet(setOf(gtvPath))
        val orgGtvArr = ArrayToGtvBinaryTreeHelper.buildGtvArrOf7WithInner3()
        
        val expectedTree =
                "                               +                                                               $ln" +
                "                              / \\                               $ln" +
                "                             /   \\                              $ln" +
                "                            /     \\                             $ln" +
                "                           /       \\                            $ln" +
                "                          /         \\                           $ln" +
                "                         /           \\                          $ln" +
                "                        /             \\                         $ln" +
                "                       /               \\                        $ln" +
                "                      /                 \\                       $ln" +
                "                     /                   \\                      $ln" +
                "                    /                     \\                     $ln" +
                "                   /                       \\                    $ln" +
                "                  /                         \\                   $ln" +
                "                 /                           \\                  $ln" +
                "                /                             \\                 $ln" +
                "               /                               \\                $ln" +
                "               +                               0102040804090309                               $ln" +
                "              / \\                                               $ln" +
                "             /   \\                                              $ln" +
                "            /     \\                                             $ln" +
                "           /       \\                                            $ln" +
                "          /         \\                                           $ln" +
                "         /           \\                                          $ln" +
                "        /             \\                                         $ln" +
                "       /               \\                                        $ln" +
                "       0103030304               +               .               .               $ln" +
                "                      / \\                                       $ln" +
                "                     /   \\                                      $ln" +
                "                    /     \\                                     $ln" +
                "                   /       \\                                    $ln" +
                "   .       .       0204       *       .       .       .       .       $ln" +
                "                          / \\                                   $ln" +
                "                         /   \\                                  $ln" +
                " .   .   .   .   .   .   +   0204   .   .   .   .   .   .   .   .   $ln" +
                "                        / \\                                     $ln" +
                "- - - - - - - - - - - - 0202 *9 - - - - - - - - - - - - - - - - - - "


        val merkleProofTree:GtvMerkleProofTree = orgGtvArr.generateProof(gtvPaths, calculator)

        // Print the result tree
        val printer = TreePrinter()
        val pbt = PrintableTreeFactory.buildPrintableTreeFromProofTree(merkleProofTree)
        val resultPrintout = printer.printNode(pbt)
        //println(resultPrintout)

       assertEquals(expectedTree.trim(), resultPrintout.trim())

        // Make sure the merkle root stays the same as without proof
        val merkleProofRoot = merkleProofTree.merkleHash(calculator)
        assertEquals(expectet7and3ElementArrayMerkleRoot, TreeHelper.convertToHex(merkleProofRoot))
    }


    @Test
    fun test_ArrayLength7_withInnerLength3Array_path2three() {
        val calculator = MerkleHashCalculatorDummy()

        val path: Array<Any> = arrayOf(2)
        val gtvPath: GtvPath = GtvPathFactory.buildFromArrayOfPointers(path)
        val gtvPaths = GtvPathSet(setOf(gtvPath))
        val orgGtvArr = ArrayToGtvBinaryTreeHelper.buildGtvArrOf7WithInner3()

        // How to calculate the hash of the sub tree?
        // see test_ArrayLength7_withInnerLength3Array_root
        // == 08020404040C0305

        val expectedTree =
                "       +               $ln" +
                "      / \\       $ln" +
                "     /   \\      $ln" +
                "    /     \\     $ln" +
                "   /       \\    $ln" +
                "   +       0102040804090309       $ln" +
                "  / \\           $ln" +
                " /   \\          $ln" +
                " 0103030304   +   .   .   $ln" +
                "    / \\         $ln" +
                "- - *3 08020404040C0305 - - - - "


        val merkleProofTree = orgGtvArr.generateProof(gtvPaths, calculator)

        // Print the result tree
        val printer = TreePrinter()
        val pbt = PrintableTreeFactory.buildPrintableTreeFromProofTree(merkleProofTree)
        val resultPrintout = printer.printNode(pbt)
        //println(resultPrintout)

       assertEquals(expectedTree.trim(), resultPrintout.trim())

        // Make sure the merkle root stays the same as without proof
        val merkleProofRoot = merkleProofTree.merkleHash(calculator)
        assertEquals(expectet7and3ElementArrayMerkleRoot, TreeHelper.convertToHex(merkleProofRoot))
    }


    // ---------------------
    // 4. Later we test to build a proof where the value-to-be-proved is a complex type (another array)
    // ---------------------

    @Test
    fun test_ArrayLength7_withInnerLength3Array_path2subArray() {
        val calculator = MerkleHashCalculatorDummy()

        val path: Array<Any> = arrayOf(3)
        val gtvPath: GtvPath = GtvPathFactory.buildFromArrayOfPointers(path)
        val gtvPaths = GtvPathSet(setOf(gtvPath))
        val orgGtvArr = ArrayToGtvBinaryTreeHelper.buildGtvArrOf7WithInner3()

        val expectedTree ="       +               $ln" +
                "      / \\       $ln" +
                "     /   \\      $ln" +
                "    /     \\     $ln" +
                "   /       \\    $ln" +
                "   +       0102040804090309       $ln" +
                "  / \\           $ln" +
                " /   \\          $ln" +
                " 0103030304   +   .   .   $ln" +
                "    / \\         $ln" +
                "- - 0204 *[1,9,3] - - - - "


        val merkleProofTree = orgGtvArr.generateProof(gtvPaths, calculator)

        // Print the result tree
        val printer = TreePrinter()
        val pbt = PrintableTreeFactory.buildPrintableTreeFromProofTree(merkleProofTree)
        val resultPrintout = printer.printNode(pbt)
        //println(resultPrintout)

       assertEquals(expectedTree.trim(), resultPrintout.trim())

        // Make sure the merkle root stays the same as without proof
        val merkleProofRoot = merkleProofTree.merkleHash(calculator)
        assertEquals(expectet7and3ElementArrayMerkleRoot, TreeHelper.convertToHex(merkleProofRoot))

        // Proof -> Serialize
        val serialize: GtvArray = merkleProofTree.toGtv()
        println("Serilalized: $serialize")

        val expectedSerialization = "[$ln" +
                "  103, $ln" + // 103 = array head node type
                "  7, $ln" + // length of array
                "  -10, $ln" + // no path elem
                "  [$ln" +
                "    102, $ln" + // 102 = dummy node
                "    [$ln" +
                "      100, $ln" + // 100 = hash
                "      x\"0103030304\"$ln" +
                "    ],$ln" +
                "    [$ln" +
                "      102, $ln" + // 102 = dummy node
                "      [$ln" +
                "        100, $ln" + // 100 = hash
                "        x\"0204\"$ln" +
                "      ], $ln" +
                "      [$ln" +
                "        101, $ln" + // 101 = value to prove
                "        3, $ln" + // path elem = 2
                "        [$ln" +  // Here the value to prove is a regular GtvArray. Interesting to see that this is deserialized propely (i.e. kept)
                "          1, $ln" +
                "          9, $ln" +
                "          3$ln" +
                "        ]$ln" +
                "      ]$ln" +
                "    ]$ln" +
                "  ], $ln" +
                "  [$ln" +
                "    100, $ln" + // 100 = hash
                "    x\"0102040804090309\"$ln" +
                "  ]$ln" +
                "]$ln"

       assertEquals(stripWhite(expectedSerialization), stripWhite(serialize.toString())) // Not really needed, Can be removed

        // Serialize -> deserialize
        val deserialized = proofFactory.deserialize(serialize)


        // Print the result tree
        val pbtDes = PrintableTreeFactory.buildPrintableTreeFromProofTree(deserialized)
        val deserializedPrintout = printer.printNode(pbtDes)
        println(deserializedPrintout)

       assertEquals(expectedTree.trim(), deserializedPrintout.trim())

    }
}