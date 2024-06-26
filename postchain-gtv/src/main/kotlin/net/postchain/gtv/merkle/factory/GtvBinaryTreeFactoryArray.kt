// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv.merkle.factory

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.merkle.*
import net.postchain.gtv.merkle.path.PathElement
import net.postchain.gtv.merkle.path.GtvPathSet

object GtvBinaryTreeFactoryArray {

    private val mainFactory = GtvBinaryTreeFactory()

    /**
     * There are 2 edge cases here:
     * - When the args is empty. -> We return a top node with two empty leaves
     * - When there is only one element. -> We set the right element as empty
     */
    fun buildFromGtvArray(gtvArray: GtvArray, gtvPaths: GtvPathSet): BinaryTreeElement {
        val pathElem: PathElement? =  gtvPaths.getPathLeafOrElseAnyCurrentPathElement()

        // 1. Build leaf layer
        val leafList: List<Gtv> = gtvArray.array.map {it}
        if (leafList.isEmpty()) {
            return GtvArrayHeadNode(EmptyLeaf, EmptyLeaf, gtvArray, 0, 0, pathElem)
        }

        val leafArray = mainFactory.buildLeafElements(leafList, gtvPaths)
        val sumNrOfBytes = leafArray.sumOf { it.getNrOfBytes() }

        // 2. Build all higher layers
        val result = mainFactory.buildHigherLayer(1, leafArray)

        // 3. Fix and return the root node
        return when (val orgRoot = result[0]) {
            is Node -> {
                GtvArrayHeadNode(orgRoot.left, orgRoot.right, gtvArray, leafList.size, sumNrOfBytes, pathElem)
            }
            is Leaf<*> -> {
                buildFromOneLeaf(leafList, orgRoot, gtvArray, sumNrOfBytes, pathElem)
            }
            else -> throw IllegalStateException("Should not find element of this type here: $orgRoot")
        }
    }

    private fun buildFromOneLeaf(leafList: List<Gtv>, orgRoot: BinaryTreeElement, gtvArray: GtvArray, sumNrOfBytes: Int, pathElem: PathElement?): GtvArrayHeadNode {
        return if (leafList.size > 1) {
            throw IllegalStateException("How come we got a leaf returned when we had ${leafList.size} elements is the args?")
        } else {
            GtvArrayHeadNode(orgRoot, EmptyLeaf, gtvArray, leafList.size, sumNrOfBytes, pathElem)
        }
    }

}