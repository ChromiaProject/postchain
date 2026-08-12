package net.postchain.gtv.merkle.factory

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvString
import net.postchain.gtv.merkle.BinaryTreeElement
import net.postchain.gtv.merkle.EmptyLeaf
import net.postchain.gtv.merkle.GtvBinaryTreeFactory
import net.postchain.gtv.merkle.GtvDictHeadNode
import net.postchain.gtv.merkle.Node
import net.postchain.gtv.merkle.path.GtvPath
import net.postchain.gtv.merkle.path.GtvPathSet

public object GtvBinaryTreeFactoryDict {

    /**
     * Key and value are both treated as leaves and added to the tree.
     *
     * There is one edge case: when the dict is empty we return a top node with two empty leaves.
     */
    public fun buildFromGtvDictionary(
        gtvDictionary: GtvDictionary,
        gtvPaths: GtvPathSet,
        gtvBinaryTreeFactory: GtvBinaryTreeFactory,
    ): GtvDictHeadNode {
        val pathElem = gtvPaths.getPathLeafOrElseAnyCurrentPathElement()
        // Needs to be sorted, or else the order is undefined
        val keys: List<String> = gtvDictionary.dict.keys.sorted()

        if (keys.isEmpty()) {
            return GtvDictHeadNode(EmptyLeaf, EmptyLeaf, gtvDictionary, keys.size, 0, pathElem)
        }

        // 1. Build first (leaf) layer
        val leafArray = buildLeafElementFromDict(keys, gtvDictionary, gtvPaths, gtvBinaryTreeFactory)
        val sumNrOfBytes = leafArray.sumOf { it.getNrOfBytes() }

        // 2. Build all higher layers
        val result = gtvBinaryTreeFactory.buildHigherLayer(1, leafArray)

        // 3. Fix and return the root node
        return when (val orgRoot = result[0]) {
            is Node -> GtvDictHeadNode(orgRoot.left, orgRoot.right, gtvDictionary, keys.size, sumNrOfBytes, pathElem)
            else -> throw IllegalStateException("Should not find element of this type here: $orgRoot")
        }
    }

    /**
     * Converts the key-value pairs of a [GtvDictionary] into [BinaryTreeElement]s, dropping the paths that are
     * irrelevant for each pair.
     */
    private fun buildLeafElementFromDict(
        keys: List<String>,
        gtvDictionary: GtvDictionary,
        gtvPaths: GtvPathSet,
        gtvBinaryTreeFactory: GtvBinaryTreeFactory,
    ): ArrayList<BinaryTreeElement> {
        val leafArray = ArrayList<BinaryTreeElement>(keys.size * 2)

        val onlyDictPaths = gtvPaths.keepOnlyDictPaths() // For performance, since we will loop soon

        for (key in keys) {
            // 1.a Fix the key. The key cannot be proved, so NO_PATHS
            val keyGtvString: Gtv = GtvString(key)
            leafArray += gtvBinaryTreeFactory.handleLeaf(keyGtvString, GtvPath.NO_PATHS)

            // 1.b Fix the value/content
            val pathsRelevantForThisLeaf = onlyDictPaths.getTailIfFirstElementIsDictOfThisKeyFromList(key)
            val content: Gtv = gtvDictionary[key]!!
            leafArray += gtvBinaryTreeFactory.handleLeaf(content, pathsRelevantForThisLeaf)
        }

        return leafArray
    }
}
