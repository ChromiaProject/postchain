// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv.merkle.tree

import net.postchain.gtv.merkle.*
import net.postchain.gtv.merkle.path.GtvPath
import net.postchain.gtv.merkle.path.GtvPathFactory
import net.postchain.gtv.merkle.path.GtvPathSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MixArrayDictToGtvBinaryTreeTest {

    private val ln = System.lineSeparator()
    private val factory = GtvBinaryTreeFactory()

    /**
     * An arrays within a dict.
     */
    private fun buildTreeOfDict1WithSubArray4(): String {
        return buildTreeOfDict1WithSubArray4(null)
    }

    private fun buildTreeOfDict1WithSubArray4(gtvPath: GtvPath?): String {
        val gtvDict = MixArrayDictToGtvBinaryTreeHelper.buildGtvDictWithSubArray4()

        val fullBinaryTree: GtvBinaryTree = if (gtvPath != null) {
            factory.buildFromGtvAndPath(gtvDict, GtvPathSet(setOf(gtvPath)))
        } else {
            factory.buildFromGtv(gtvDict)
        }

        val printer = TreePrinter()
        val printableBinaryTree = PrintableTreeFactory.buildPrintableTreeFromClfbTree(fullBinaryTree)
        val treePrintout = printer.printNode(printableBinaryTree)
        //println(treePrintout)
        return treePrintout
    }

    @Test
    fun testIntDictLength1withInnerLength4Array() {
        val expectedTree =
                "       +               $ln" +
                        "      / \\       $ln" +
                        "     /   \\      $ln" +
                        "    /     \\     $ln" +
                        "   /       \\    $ln" +
                        "   one       +       $ln" +
                        "          / \\   $ln" +
                        "         /   \\  $ln" +
                        " .   .   +   +   $ln" +
                        "        / \\ / \\ $ln" +
                        "- - - - 1 2 3 4 "

        val treePrintout = buildTreeOfDict1WithSubArray4()
        //println(treeHolder.treePrintout)
       assertEquals(expectedTree.trim(), treePrintout.trim())
    }

    @Test
    fun testIntDictLength1withInnerLength4Array_withPath() {
        val path: Array<Any> = arrayOf("one", 3)

        val expectedTreeWithPath = "       *               $ln" +
                "      / \\       $ln" +
                "     /   \\      $ln" +
                "    /     \\     $ln" +
                "   /       \\    $ln" +
                "   one       *       $ln" +
                "          / \\   $ln" +
                "         /   \\  $ln" +
                " .   .   +   +   $ln" +
                "        / \\ / \\ $ln" +
                "- - - - 1 2 3 *4 "

        val gtvPath: GtvPath = GtvPathFactory.buildFromArrayOfPointers(path)
        val treePrintout = buildTreeOfDict1WithSubArray4(gtvPath)
        //println(treeHolder.treePrintout)

       assertEquals(expectedTreeWithPath.trim(), treePrintout.trim())
    }
}