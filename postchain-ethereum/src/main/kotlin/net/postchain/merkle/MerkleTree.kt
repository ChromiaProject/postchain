package net.postchain.merkle

import net.postchain.common.data.Hash
import net.postchain.crypto.DigestSystem

class MerkleTree(private val ds: DigestSystem) {
    // Child trees
    private var leftTree: MerkleTree? = null
    private var rightTree: MerkleTree? = null

    // Child leaves
    private var leftLeaf: Leaf? = null
    private var rightLeaf: Leaf? = null

    // The hash value of this node
    private lateinit var hash: ByteArray

    /**
     * Adds two child subtrees to this Merkle Tree.
     *
     * @param leftTree The left child tree
     * @param rightTree The right child tree
     */
    fun add(leftTree: MerkleTree, rightTree: MerkleTree) {
        this.leftTree = leftTree
        this.rightTree = rightTree

        // Calculate the message digest using the
        // specified digest algorithm and the
        // contents of the two child nodes
        hash = ds.hash(leftTree.hash, rightTree.hash)
    }

    /**
     * Adds two child leaves to this Merkle Tree.
     *
     * @param leftLeaf The left child leaf
     * @param rightLeaf The right child leaf
     */
    fun add(leftLeaf: Leaf, rightLeaf: Leaf) {
        this.leftLeaf = leftLeaf
        this.rightLeaf = rightLeaf

        // Calculate the message digest using the
        // specified digest algorithm and the
        // contents of the two child nodes
        hash = ds.hash(leftLeaf.hash, rightLeaf.hash)
    }

    fun digest(): Hash {
        return hash
    }
}