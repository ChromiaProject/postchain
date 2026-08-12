package net.postchain.gtv.merkle

import mu.KLogging
import net.postchain.gtv.GtvPrimitive
import net.postchain.gtv.merkle.path.PathLeafElement
import net.postchain.gtv.merkle.path.PathSet

/**
 * Converts a list of elements into a tree of elements. Sub class for each type of element you want to build.
 */
public abstract class BinaryTreeFactory<T, TPathSet : PathSet> : KLogging() {

    /**
     * Wraps the incoming leaf into a [BinaryTreeElement]. Recursive if the leaf is itself a complex object.
     *
     * @param isRoot tells us if this is the top element
     */
    public fun handleLeaf(leaf: T, paths: TPathSet, isRoot: Boolean = false): BinaryTreeElement =
        if (paths.isEmpty() && !isRoot && leaf is GtvPrimitive) {
            innerHandleLeaf(leaf, getEmptyPathSet())
        } else {
            innerHandleLeaf(leaf, paths)
        }

    protected abstract fun getEmptyPathSet(): TPathSet

    protected abstract fun innerHandleLeaf(leaf: T, paths: TPathSet): BinaryTreeElement

    /**
     * Just like [handleLeaf] but we know that this leaf is not a complex type.
     */
    public fun handlePrimitiveLeaf(leaf: T, paths: TPathSet): BinaryTreeElement {
        val pathElem = paths.getPathLeafOrElseAnyCurrentPathElement()
        if (pathElem != null && pathElem !is PathLeafElement) {
            throw IllegalArgumentException(
                "Path does not match the tree structure. We are at a leaf $leaf but found path element $pathElem"
            )
        }
        return Leaf(leaf, getNrOfBytes(leaf), pathElem)
    }

    /** @return number of bytes this leaf consumes */
    public abstract fun getNrOfBytes(leaf: T): Int

    /**
     * Calls itself until the return value only holds 1 element.
     *
     * Note: this method can only create standard [Node]s that fill up the area between the "top" and the leaves.
     * These "in-between" nodes cannot be path leaves or have any interesting properties.
     */
    public fun buildHigherLayer(layer: Int, inList: List<BinaryTreeElement>): List<BinaryTreeElement> {
        if (inList.isEmpty()) throw IllegalStateException("Cannot work on empty arrays. Layer: $layer")
        if (inList.size == 1) return inList

        val returnArray = ArrayList<BinaryTreeElement>()
        var leftValue: BinaryTreeElement? = null
        var isLeft = true

        for (element in inList) {
            if (isLeft) {
                leftValue = element
                isLeft = false
            } else {
                returnArray += Node(leftValue!!, element)
                isLeft = true
                leftValue = null
            }
        }

        // If there is an odd number of nodes, move the last node up one level
        if (!isLeft) returnArray += leftValue!!

        return buildHigherLayer(layer + 1, returnArray)
    }

    public abstract fun buildBinaryTree(gtv: T, gtvPaths: TPathSet): BinaryTree<T>
}
