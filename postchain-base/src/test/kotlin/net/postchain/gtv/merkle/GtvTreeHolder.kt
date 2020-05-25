// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv.merkle

import net.postchain.base.merkle.TreeHolder
import net.postchain.base.merkle.TreeHolderWithIntArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDictionary


open class TreeHolderFromArray(orgIntArray: IntArray,
                               clfbTree:GtvBinaryTree,
                               treePrintout: String,
                               expectedPrintout: String,
                               val orgGtvArray: GtvArray<Gtv>):
        TreeHolderWithIntArray(orgIntArray, clfbTree, treePrintout, expectedPrintout)

class TreeHolderSubTree(orgIntArray: IntArray,
                        clfbTree:GtvBinaryTree,
                        treePrintout: String,
                        expectedPrintout: String,
                        orgSubGtvArray: GtvArray<Gtv>,
                        val orgGtvList: MutableList<Gtv>):
        TreeHolderFromArray (orgIntArray,  clfbTree, treePrintout, expectedPrintout, orgSubGtvArray)


class TreeHolderFromDict( clfbTree:GtvBinaryTree,
                         treePrintout: String,
                         expectedPrintout: String,
                         val orgGtvDict: GtvDictionary):
        TreeHolder(clfbTree, treePrintout, expectedPrintout)

