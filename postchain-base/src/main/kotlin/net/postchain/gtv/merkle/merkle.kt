package net.postchain.gtv.merkle

import net.postchain.base.snapshot.DigestSystem
import net.postchain.common.data.EMPTY_HASH
import net.postchain.common.data.Hash
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder.encodeGtv
import net.postchain.gtv.GtvString

class MerkleTree(
    val data: Map<String, Gtv>,
    val ds: DigestSystem
) {
    var nodes: Array<Hash> = Array(upperPowerOfTwo(data.size)) { EMPTY_HASH }
    var layers: Array<Array<Hash>>

    init {
        val keys = data.keys.sorted()
        for ((index, key) in keys.withIndex()) {
            nodes[index] = ds.hash(
                ds.digest(encodeGtv(GtvString(key))),
                ds.digest(encodeGtv(data[key]!!))
            )
        }

        val level = merkleLevel(nodes.size)
        layers = Array(level) { arrayOf() }
        var levels = nodes
        for (i in level-1 downTo 0) {
            layers[i] = levels
            levels = Array(levels.size/2) { EMPTY_HASH }
            for (j in levels.indices) {
                levels[j] = ds.hash(layers[i][2*j], layers[i][2*j+1])
            }
        }
    }

    fun getMerklePath(key: String): Int {
        val keys = data.keys.sorted()
        for ((pos, k) in keys.withIndex()) {
            if (k == key) return pos
        }
        return -1
    }

    fun getMerkleProof(index: Int): List<Hash> {
        var idx = index
        val proof = mutableListOf<Hash>()
        if (index <= -1) return listOf()
        for (i in layers.size-1 downTo 1) {
            val pos = if (idx % 2 == 0) idx+1 else idx-1
            proof.add(layers[i][pos])
            idx /= 2
        }
        return proof
    }

    fun getMerkleRoot(): Hash {
        return layers[0][0]
    }

    fun verifyMerkleProof(proofs: List<Hash>, pos: Int, leaf: Hash): Boolean {
        var r = leaf
        proofs.forEachIndexed { i, h ->
            r = if (((pos shr i) and 1) != 0) {
                ds.hash(h, r)
            } else {
                ds.hash(r, h)
            }
        }
        return r.contentEquals(getMerkleRoot())
    }

    private fun upperPowerOfTwo(x: Int): Int {
        var p = 1
        while (p < x) p = p shl 1
        return p
    }

    private fun merkleLevel(n: Int): Int {
        var l = 1
        var t = n
        while (t > 1) {
            t = t shr 1
            l++
        }
        return l
    }
}