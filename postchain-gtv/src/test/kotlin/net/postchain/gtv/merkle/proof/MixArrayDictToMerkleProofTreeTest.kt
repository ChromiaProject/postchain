// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv.merkle.proof

import net.postchain.gtv.generateProof
import net.postchain.gtv.merkle.*
import net.postchain.gtv.merkle.path.GtvPath
import net.postchain.gtv.merkle.path.GtvPathFactory
import net.postchain.gtv.merkle.path.GtvPathSet
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * We will generate proofs from mixes of Dict and Arrays
 */
class MixArrayDictToMerkleProofTreeTest {

    private val ln = System.lineSeparator()

    @Test
    fun test_dictWithArr_where_path_is_to_leaf4() {
        val calculator = MerkleHashCalculatorDummy()

        val path: Array<Any> = arrayOf("one", 3)
        val gtvPath = GtvPathFactory.buildFromArrayOfPointers(path)
        val gtvPaths = GtvPathSet(setOf(gtvPath))
        val gtvDict = MixArrayDictToGtvBinaryTreeHelper.buildGtvDictWithSubArray4()

        val expectedTree = "       +               $ln" +
                "      / \\       $ln" +
                "     /   \\      $ln" +
                "    /     \\     $ln" +
                "   /       \\    $ln" +
                "   02706F66       *       $ln" +
                "          / \\   $ln" +
                "         /   \\  $ln" +
                " .   .   0103030304   +   $ln" +
                "            / \\ $ln" +
                "- - - - - - 0204 *4 "

        val merkleProofTree = gtvDict.generateProof(gtvPaths, calculator)

        // Print the result tree
        val printer = TreePrinter()
        val pbt = PrintableTreeFactory.buildPrintableTreeFromProofTree(merkleProofTree)
        val resultPrintout = printer.printNode(pbt)
        //println(resultPrintout)

       assertEquals(expectedTree.trim(), resultPrintout.trim())

        // Make sure the merkle root stays the same as without proof
        val merkleProofRoot = merkleProofTree.merkleHash(calculator)
        assertEquals(MixArrayDictToGtvBinaryTreeHelper.expecedMerkleRoot_dict1_array4, TreeHelper.convertToHex(merkleProofRoot))
    }

    /**
     * Here we try to prove the entire sub array (1,2,3,4)
     */
    @Test
    fun test_dictWithArr_where_path_is_to_sub_arr() {
        val calculator = MerkleHashCalculatorDummy()

        val path: Array<Any> = arrayOf("one")
        val gtvPath: GtvPath = GtvPathFactory.buildFromArrayOfPointers(path)
        val gtvPaths = GtvPathSet(setOf(gtvPath))
        val gtvDict = MixArrayDictToGtvBinaryTreeHelper.buildGtvDictWithSubArray4()

        val expectedTree = " +   $ln" +
                "/ \\ $ln" +
                "02706F66 *[1, 2, 3, 4]"

        val merkleProofTree = gtvDict.generateProof(gtvPaths, calculator)

        // Print the result tree
        val printer = TreePrinter()
        val pbt = PrintableTreeFactory.buildPrintableTreeFromProofTree(merkleProofTree)
        val resultPrintout = printer.printNode(pbt)
        println(resultPrintout)

       assertEquals(expectedTree.trim(), resultPrintout.trim())

        // Make sure the merkle root stays the same as without proof
        val merkleProofRoot = merkleProofTree.merkleHash(calculator)
        assertEquals(MixArrayDictToGtvBinaryTreeHelper.expecedMerkleRoot_dict1_array4, TreeHelper.convertToHex(merkleProofRoot))
    }
}