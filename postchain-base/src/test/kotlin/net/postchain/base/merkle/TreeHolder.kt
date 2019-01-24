package net.postchain.base.merkle

import net.postchain.base.merkle.root.HashBinaryTree
import net.postchain.gtx.merkle.GtxBinaryTree

open class TreeHolder(val orgIntArray: IntArray,
                      val clfbTree: GtxBinaryTree,
                      val treePrintout: String,
                      val expectedPrintout: String) {


}


open class TreeHashHolder(val orgIntArray: IntArray,
                          val clfbTree: HashBinaryTree,
                          val treePrintout: String,
                          val expectedPrintout: String) {
}

open class TreeHashHolderFromArray(orgIntArray: IntArray,
                                   clfbTree: HashBinaryTree,
                                   treePrintout: String,
                                   expectedPrintout: String,
                                   val orgHashList: List<Hash> ):
        TreeHashHolder (orgIntArray, clfbTree, treePrintout, expectedPrintout) {
}