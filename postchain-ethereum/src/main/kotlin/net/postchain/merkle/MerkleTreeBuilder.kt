package net.postchain.merkle

import net.postchain.common.data.EMPTY_HASH
import net.postchain.common.data.Hash
import net.postchain.common.data.TreeHasher

class MerkleTreeBuilder(private val hasher: TreeHasher) {

    fun merkleRootHash(data: List<Hash>): Hash {
        val leafs = buildLeafs(data)
        val firstLayer = buildBottomLayer(leafs)
        val root = build(firstLayer)
        return if (root.isEmpty()) {
             EMPTY_HASH
        } else {
            root[0].digest()
        }
    }

    fun buildLeafs(data: List<Hash>): List<Leaf> {
        return data.map { Leaf(it) }
    }

    fun buildBottomLayer(leafs: List<Leaf>): List<MerkleTree> {
        val result = mutableListOf<MerkleTree>()
        val size = leafs.size
        val data: List<Leaf>
        val level= if (size % 2 == 0) {
            data = leafs
            size/2
        } else {
            data = leafs.plus(Leaf(EMPTY_HASH))
            size / 2 + 1
        }
        for (i in 0 until level) {
            val tree = MerkleTree(hasher)
            tree.add(data[i*2], data[(i*2)+1])
            result.add(tree)
        }
        return result
    }

    fun build(data: List<MerkleTree>): List<MerkleTree> {
        if (data.size <= 1) {
            return data
        }

        val result = arrayListOf<MerkleTree>()
        var leftValue: MerkleTree? = null
        var isLeft = true
        for (element: MerkleTree in data) {
            if (isLeft)  {
                leftValue = element
                isLeft = false
            } else {
                val node = MerkleTree(hasher)
                node.add(leftValue!!, element)
                result.add(node)
                isLeft = true
                leftValue = null
            }
        }

        if (!isLeft) {
            // If there is odd number of nodes, then move the last node up one level
            result.add(leftValue!!)
        }

        return build(result)
    }
}