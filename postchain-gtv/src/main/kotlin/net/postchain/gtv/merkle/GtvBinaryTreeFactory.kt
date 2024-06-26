// Copyright (c) 2020 ChromaWay AB. See README for license information.

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
 * This can build two types of trees:
 * 1. Make a binary tree out of a Gtv object graph
 * 2. Same as above, but we also marked each Gtv sub structure that should be a path leaf.
 *    If you want this option (2) you have to provide a list of [GtvPath]
 */
class GtvBinaryTreeFactory : BinaryTreeFactory<Gtv, GtvPathSet>() {

    /**
     * Generic builder.
     * @param gtv will take any damn thing
     */
    fun buildFromGtv(gtv: Gtv): GtvBinaryTree {
        return buildFromGtvAndPath(gtv, GtvPath.NO_PATHS)
    }

    /**
     * Generic builder.
     * @param gtv will take any damn thing
     * @param gtvPaths will tell us what element that are path leaves
     */
    fun buildFromGtvAndPath(gtv: Gtv, gtvPaths: GtvPathSet): GtvBinaryTree {
        if (logger.isTraceEnabled) {
            logger.trace("--------------------------------------------")
            logger.trace("--- Converting GTV to binary tree ----------")
            logger.trace("--------------------------------------------")
        }
        val result = handleLeaf(gtv, gtvPaths, true)
        if (logger.isTraceEnabled) {
            logger.trace("--------------------------------------------")
            logger.trace("--- /Converting GTV to binary tree ---------")
            logger.trace("--------------------------------------------")
        }
        return GtvBinaryTree(result)
    }


    /**
     * The generic method that builds [BinaryTreeElement] from [Gtv] s.
     * The only tricky bit of this method is that we need to remove paths that are irrelevant for the leaf in question.
     *
     * @param leafList the list of [Gtv] we will use for leafs in the tree
     * @param gtvPaths the paths we have to consider while creating the leafs
     * @return an array of all the leafs as [BinaryTreeElement] s. Note that some leafs might not be primitive values
     *   but some sort of collection with their own leafs (recursively)
     */
    fun buildLeafElements(leafList: List<Gtv>, gtvPaths: GtvPathSet): ArrayList<BinaryTreeElement> {
        val leafArray = arrayListOf<BinaryTreeElement>()

        val onlyArrayPaths = gtvPaths.keepOnlyArrayPaths() // For performance, since we will loop soon

        for (i in leafList.indices) {
            val pathsRelevantForThisLeaf = onlyArrayPaths.getTailIfFirstElementIsArrayOfThisIndexFromList(i)
            val leaf = leafList[i]
            val binaryTreeElement = handleLeaf(leaf, pathsRelevantForThisLeaf)
            leafArray.add(binaryTreeElement)
        }
        return leafArray
    }

    override fun getEmptyPathSet(): GtvPathSet = GtvPath.NO_PATHS

    /**
     * At this point we should have looked in cache.
     *
     * @param leaf we should turn into a tree element
     * @param paths
     * @return the tree element we created.
     */
    override fun innerHandleLeaf(leaf: Gtv, paths: GtvPathSet): BinaryTreeElement {
        return when (leaf) {
            is GtvPrimitive -> handlePrimitiveLeaf(leaf, paths)
            is GtvArray -> GtvBinaryTreeFactoryArray.buildFromGtvArray(leaf, paths)
            is GtvDictionary -> GtvBinaryTreeFactoryDict.buildFromGtvDictionary(leaf, paths)
            is GtvCollection -> throw IllegalStateException("Programmer should have dealt with this container type: ${leaf.type}")
            else -> throw IllegalStateException("What is this? Not container and not primitive? type: ${leaf.type}")
        }
    }

    override fun getNrOfBytes(leaf: Gtv): Int {
        return leaf.nrOfBytes()
    }


}