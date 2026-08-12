package net.postchain.gtv.merkle

import net.postchain.gtv.merkle.MerkleBasics.HASH_PREFIX_LEAF
import net.postchain.gtv.merkle.MerkleBasics.HASH_PREFIX_NODE
import net.postchain.gtv.merkle.path.PathElement
import net.postchain.gtv.merkle.path.PathLeafElement

/**
 * A "full" binary tree that stores values in the leaves only — every node has either two children or none.
 * The exception to the "empty nodes" rule is that collections are stored in nodes so they can be proven.
 *
 * The tree is filled from left to right:
 *
 * ```
 *              root
 *            /      \
 *      node1234    node567
 *      /     \      /    \
 *  node12 node34 node56   7
 *   / \    /  \    /  \
 *  1   2  3    4  5    6
 * ```
 *
 * These trees are typically neither balanced nor complete, since arrays of arrays become trees.
 */
public open class BinaryTreeElement {

    /**
     * Tells us if this element is part of a path.
     *
     * Note: strictly there might be multiple paths leading down from this object, but we have no need to keep
     * track of that, so we just pick one.
     */
    private var pathElem: PathElement? = null

    public fun getPathElement(): PathElement? = pathElem

    public fun isPath(): Boolean = pathElem != null

    /** @return true if this element is a leaf of a path (i.e. it should be proven). */
    public fun isPathLeaf(): Boolean = pathElem is PathLeafElement

    /** Protected for a reason: we want to be very strict about when and who sets the "pathElem". */
    protected fun setPathElement(pathElem: PathElement?) {
        this.pathElem = pathElem
    }

    public open fun getPrefixByte(): Byte = HASH_PREFIX_NODE // Usually overridden

    public open fun getNrOfBytes(): Int = throw IllegalStateException("Should implement this in sub class")
}

/**
 * Super type of binary (parent) nodes. Doesn't hold any content.
 */
public open class Node(
    public val left: BinaryTreeElement,
    public val right: BinaryTreeElement,
) : BinaryTreeElement() {

    override fun getPrefixByte(): Byte = HASH_PREFIX_NODE
}

/**
 * A node that is the root of its own sub tree. It can be proven, and keeps a reference to the original structure.
 */
public open class SubTreeRootNode<T>(
    left: BinaryTreeElement,
    right: BinaryTreeElement,
    public val content: T,
    pathElem: PathElement? = null,
) : Node(left, right) {

    init {
        setPathElement(pathElem)
    }
}

/**
 * Holds content of type [T].
 *
 * @property pathElem must be the END DESTINATION of the path, if the leaf is part of one.
 */
public class Leaf<T>(
    public val content: T,
    public val sizeInBytes: Int,
    pathElem: PathElement? = null,
) : BinaryTreeElement() {

    init {
        if (pathElem != null) {
            if (pathElem is PathLeafElement) {
                setPathElement(pathElem)
            } else {
                throw IllegalArgumentException(
                    "The path and object structure does not match! We are at a leaf, but the path expects a " +
                        "sub structure. Path element: $pathElem , content: $content"
                )
            }
        }
    }

    override fun getPrefixByte(): Byte = HASH_PREFIX_LEAF

    override fun getNrOfBytes(): Int = sizeInBytes
}

/**
 * Dummy filler. Will always be the right side.
 * (This is needed for the case when a collection only has one element.)
 */
public data object EmptyLeaf : BinaryTreeElement() {
    override fun getNrOfBytes(): Int = 0
}

/**
 * Wrapper class for the root object.
 */
public open class BinaryTree<T>(public val root: BinaryTreeElement) {

    /** Mostly for debugging */
    public fun maxLevel(): Int = maxLevelInternal(root)

    private fun maxLevelInternal(node: BinaryTreeElement): Int = when (node) {
        is EmptyLeaf -> 0 // Doesn't count
        is Leaf<*> -> 1
        is Node -> maxOf(maxLevelInternal(node.left), maxLevelInternal(node.right)) + 1
        else -> throw IllegalStateException("What is this type? $node")
    }
}
