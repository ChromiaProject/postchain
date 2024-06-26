// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv.merkle

open class TreeHolder(val clfbTree: GtvBinaryTree,
                      val treePrintout: String,
                      val expectedPrintout: String)

open class TreeHolderWithIntArray(
        val orgIntArray: IntArray,
        clfbTree:GtvBinaryTree,
        treePrintout: String,
        expectedPrintout: String
): TreeHolder(clfbTree, treePrintout, expectedPrintout)



