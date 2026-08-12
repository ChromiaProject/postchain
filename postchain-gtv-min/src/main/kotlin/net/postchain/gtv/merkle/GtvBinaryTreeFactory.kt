package net.postchain.gtv.merkle

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvCollection
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvPrimitive
import net.postchain.gtv.merkle.factory.GtvBinaryTreeFactoryArray
import net.postchain.gtv.merkle.factory.GtvBinaryTreeFactoryDict
import net.postchain.gtv.merkle.path.GtvPath
import net.postchain.gtv.merkle.path.GtvPathSet

/**
 * Builds two types of trees:
 * 1. a binary tree out of a Gtv object graph
 * 2. the same, but with each Gtv sub structure that should be a path leaf marked as such
 */
public class GtvBinaryTreeFactory(public val gtvHashVersion: Int) : BinaryTreeFactory<Gtv, GtvPathSet>() {

    public fun buildFromGtv(gtv: Gtv): GtvBinaryTree = buildBinaryTree(gtv, GtvPath.NO_PATHS)

    override fun buildBinaryTree(gtv: Gtv, gtvPaths: GtvPathSet): GtvBinaryTree =
        GtvBinaryTree(handleLeaf(gtv, gtvPaths, true))

    /**
     * Builds [BinaryTreeElement]s from [Gtv]s, dropping the paths that are irrelevant for each leaf.
     *
     * Note that some leaves might not be primitive values but collections with their own leaves (recursively).
     */
    public fun buildLeafElements(leafList: List<Gtv>, gtvPaths: GtvPathSet): ArrayList<BinaryTreeElement> {
        val onlyArrayPaths = gtvPaths.keepOnlyArrayPaths() // For performance, since we will loop soon

        val leafArray = leafList.indices.mapTo(ArrayList(leafList.size)) {
            val pathsRelevantForThisLeaf = onlyArrayPaths.getTailIfFirstElementIsArrayOfThisIndexFromList(it)
            handleLeaf(leafList[it], pathsRelevantForThisLeaf)
        }

        return leafArray
    }

    override fun getEmptyPathSet(): GtvPathSet = GtvPath.NO_PATHS

    override fun innerHandleLeaf(leaf: Gtv, paths: GtvPathSet): BinaryTreeElement = when (leaf) {
        is GtvPrimitive -> handlePrimitiveLeaf(leaf, paths)
        is GtvArray -> GtvBinaryTreeFactoryArray.buildFromGtvArray(leaf, paths, this)
        is GtvDictionary -> GtvBinaryTreeFactoryDict.buildFromGtvDictionary(leaf, paths, this)
        is GtvCollection -> throw IllegalStateException(
            "Programmer should have dealt with this container type: ${leaf.type}"
        )

        else -> throw IllegalStateException("What is this? Not container and not primitive? type: ${leaf.type}")
    }

    override fun getNrOfBytes(leaf: Gtv): Int = leaf.nrOfBytes()
}
