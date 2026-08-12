package net.postchain.gtv.merkle.factory

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.merkle.BinaryTreeElement
import net.postchain.gtv.merkle.EmptyLeaf
import net.postchain.gtv.merkle.GtvArrayHeadNode
import net.postchain.gtv.merkle.GtvBinaryTreeFactory
import net.postchain.gtv.merkle.Leaf
import net.postchain.gtv.merkle.Node
import net.postchain.gtv.merkle.path.GtvPathSet
import net.postchain.gtv.merkle.path.PathElement

public object GtvBinaryTreeFactoryArray {

    /**
     * There are 2 edge cases here:
     * - When the array is empty -> we return a top node with two empty leaves
     * - When there is only one element -> we set the right element as empty
     */
    public fun buildFromGtvArray(
        gtvArray: GtvArray,
        gtvPaths: GtvPathSet,
        gtvBinaryTreeFactory: GtvBinaryTreeFactory,
    ): BinaryTreeElement {
        val pathElem: PathElement? = gtvPaths.getPathLeafOrElseAnyCurrentPathElement()

        // 1. Build leaf layer
        val leafList: List<Gtv> = gtvArray.array.map { it }
        if (leafList.isEmpty()) {
            return GtvArrayHeadNode(EmptyLeaf, EmptyLeaf, gtvArray, 0, 0, pathElem)
        }

        val leafArray = gtvBinaryTreeFactory.buildLeafElements(leafList, gtvPaths)
        val sumNrOfBytes = leafArray.sumOf { it.getNrOfBytes() }

        // If we have just a single leaf that is a node we can return immediately (version 2)
        if (leafArray.size == 1 && leafArray[0] is Node && gtvBinaryTreeFactory.gtvHashVersion > 1) {
            return GtvArrayHeadNode(leafArray[0], EmptyLeaf, gtvArray, leafList.size, sumNrOfBytes, pathElem)
        }

        // 2. Build all higher layers
        val result = gtvBinaryTreeFactory.buildHigherLayer(1, leafArray)

        // 3. Fix and return the root node
        return when (val orgRoot = result[0]) {
            is Node -> GtvArrayHeadNode(orgRoot.left, orgRoot.right, gtvArray, leafList.size, sumNrOfBytes, pathElem)
            is Leaf<*> -> buildFromOneLeaf(leafList, orgRoot, gtvArray, sumNrOfBytes, pathElem)
            else -> throw IllegalStateException("Should not find element of this type here: $orgRoot")
        }
    }

    private fun buildFromOneLeaf(
        leafList: List<Gtv>,
        orgRoot: BinaryTreeElement,
        gtvArray: GtvArray,
        sumNrOfBytes: Int,
        pathElem: PathElement?,
    ): GtvArrayHeadNode {
        if (leafList.size > 1) {
            throw IllegalStateException(
                "How come we got a leaf returned when we had ${leafList.size} elements is the args?"
            )
        }
        return GtvArrayHeadNode(orgRoot, EmptyLeaf, gtvArray, leafList.size, sumNrOfBytes, pathElem)
    }
}
