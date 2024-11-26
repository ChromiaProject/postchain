package net.postchain.base.snapshot

import net.postchain.common.data.EMPTY_HASH
import net.postchain.common.data.Hash
import net.postchain.common.data.KECCAK256
import net.postchain.common.toHex
import java.security.MessageDigest

internal val ds: DigestSystem = SimpleDigestSystem(MessageDigest.getInstance(KECCAK256))

/**
 * To test sparse Merkle trees, we use full 4-ary trees
 */
@Suppress("ArrayInDataClass")
data class Node(
        val vertex: Array<Hash> = Array(4) { EMPTY_HASH }
) {
    constructor(vertex: List<Hash>) : this(vertex.toTypedArray())

    fun hash(): Hash = ds.hash(ds.hash(vertex[0], vertex[1]), ds.hash(vertex[2], vertex[3]))
    fun str(): String = vertex.joinToString(", ") { it.toHex().substring(0, 4) }
}

internal fun calculateMerkleRoot(states: Map<Long, Hash>, pageSize: Int = 4): Hash {
    require(pageSize == 4) { "pageSize must be 4" }

    fun merkleRootHash(nodes: List<Node>): Hash {
        if (nodes.size == 1) return nodes.first().hash()

        val next = mutableListOf<Node>()
        for (node in nodes.indices step pageSize) {
            val siblings = node until node + pageSize
            next.add(Node(
                    siblings.map { if (it <= nodes.lastIndex) nodes[it].hash() else Node().hash() }.toTypedArray()
            ))
        }

        return merkleRootHash(next)
    }

    // Initial setup
    val fullTree = mutableListOf<Node>()
    if (states.isEmpty()) {
        fullTree.add(Node())
    } else {
        val maxLeafs = (states.keys.max() / pageSize + 1) * pageSize
        for (node in 0 until maxLeafs step pageSize.toLong()) {
            val siblings = node until node + pageSize
            fullTree.add(Node(
                    siblings.map { states[it] ?: EMPTY_HASH }.toTypedArray()
            ))
        }
    }

    return merkleRootHash(fullTree)
}
