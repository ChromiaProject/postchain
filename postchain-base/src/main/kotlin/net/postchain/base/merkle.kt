// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.base

import net.postchain.core.ProgrammerMistake
import java.util.*

val internalNodePrefix = byteArrayOf(0)
val leafPrefix = byteArrayOf(1)
val nonExistingNodePrefix = byteArrayOf(2)
val nonExistingNodeHash = ByteArray(32)

enum class Side { LEFT, RIGHT }

class MerklePathItem(val side: Side, val hash: ByteArray)

typealias MerklePath = ArrayList<MerklePathItem>

fun log2ceil(value: Int): Int {
    return Math.ceil(Math.log10(value.toDouble()) / Math.log10(2.toDouble())).toInt()
}

fun computeMerkleRootHash(cryptoSystem: CryptoSystem, hashes: Array<ByteArray>, depth: Int = 0,
                          leafDepth: Int = log2ceil(hashes.size)): ByteArray {
    if (hashes.size == 0) {
        return ByteArray(32) // Just zeros
    }

    if (depth == leafDepth) {
        return hashes[0]
    }

    val maxLeavesPerChild = Math.pow(2.toDouble(), leafDepth.toDouble() - depth - 1).toInt()
    val prefix = if (depth == leafDepth - 1) leafPrefix else internalNodePrefix
    if (hashes.size <= maxLeavesPerChild) {
        val left = computeMerkleRootHash(cryptoSystem, hashes, depth + 1, leafDepth)
        return cryptoSystem.digest(prefix + left + nonExistingNodeHash)
    }

    val left = computeMerkleRootHash(cryptoSystem, hashes.sliceArray(IntRange(0, maxLeavesPerChild - 1)), depth + 1, leafDepth)
    val right = computeMerkleRootHash(cryptoSystem, hashes.sliceArray(IntRange(maxLeavesPerChild, hashes.lastIndex)), depth + 1, leafDepth)
    return cryptoSystem.digest(prefix + left + prefix + right)
}


fun internalMerklePath(cryptoSystem: CryptoSystem, hashes: Array<ByteArray>, targetIndex: Int, depth: Int, leafDepth: Int): MerklePath {
    val numTransactions = hashes.size

    if (depth == leafDepth) {
        return MerklePath()
    }

    val maxLeavesPerChild = 1 shl (leafDepth - depth - 1) // 2^(leafDepth - depth - 1)
    if (numTransactions <= maxLeavesPerChild) {
        val path = internalMerklePath(cryptoSystem, hashes, targetIndex, depth + 1, leafDepth)
        path.add(MerklePathItem(Side.RIGHT, nonExistingNodeHash))
        return path
    }
    val path: MerklePath
    if (targetIndex < maxLeavesPerChild) {
        path = internalMerklePath(cryptoSystem, hashes.slice(0 until maxLeavesPerChild).toTypedArray(), targetIndex, depth + 1, leafDepth)
        val right = computeMerkleRootHash(cryptoSystem, hashes.slice(maxLeavesPerChild until hashes.size).toTypedArray(), depth + 1, leafDepth)
        path.add(MerklePathItem(Side.RIGHT, right))
    } else {
        val left = computeMerkleRootHash(cryptoSystem, hashes.slice(0 until maxLeavesPerChild).toTypedArray(), depth + 1, leafDepth)
        path = internalMerklePath(cryptoSystem, hashes.slice(maxLeavesPerChild until hashes.size).toTypedArray(), targetIndex - maxLeavesPerChild, depth + 1, leafDepth)
        path.add(MerklePathItem(Side.LEFT, left))
    }
    return path
}

/*
 * a path looks like this:
 * {merklePath: [{side: <0|1>, hash: <hash buffer depth n-1>},
 *               {side: <0|1>, hash: <hash buffer depth n-2>},
 *               ...
 *               {side: <0|1>, hash: <hash buffer depth 1>}]}
 */
fun merklePath(cryptoSystem: CryptoSystem, hashes: Array<ByteArray>, target: ByteArray): MerklePath {
    if (hashes.size == 0) {
        throw ProgrammerMistake("Cannot make merkle path from empty transaction set")
    }
    val index = hashes.indexOfFirst { target.contentEquals(it) }
    if (index == -1) {
        throw ProgrammerMistake("Target is not in list of hashes")
    }

    val leafDepth = log2ceil(hashes.size)
    val path = internalMerklePath(cryptoSystem, hashes, index, 0, leafDepth)
    return path
}

fun validateMerklePath(cryptoSystem: CryptoSystem, path: MerklePath, target: ByteArray, merkleRoot: ByteArray): Boolean {
    var currentHash = target

    for (i in 0 until path.size) {
        val item = path[i]
        val prefix = if (i == 0) leafPrefix else internalNodePrefix
        currentHash = cryptoSystem.digest(if (item.side == Side.LEFT) {
            prefix + item.hash + prefix + currentHash
        } else {
            if (item.hash.contentEquals(nonExistingNodeHash)) {
                prefix + currentHash + nonExistingNodeHash
            } else {
                prefix + currentHash + prefix + item.hash
            }
        })
    }

    return merkleRoot.contentEquals(currentHash)
}
