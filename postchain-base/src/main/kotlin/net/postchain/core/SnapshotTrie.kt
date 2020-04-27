package net.postchain.core

import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.base.gtv.RowData
import net.postchain.common.toHex
import net.postchain.gtv.*
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import java.io.Serializable
import kotlin.math.max
import kotlin.math.min

const val bitsPerLevel = 2
const val eltsPerNode = 1 shl bitsPerLevel
const val bitsPerKey = 16
const val levelsInChunk = 4 // chunk will take this many bottom levels of the tree

var cryptoSystem = SECP256K1CryptoSystem()
var merkleHashCalculator = GtvMerkleHashCalculator(cryptoSystem)

sealed class TreeElement(val prefix: Prefix): Serializable {
    abstract fun hash(): String
    abstract fun cloneWithNewPrefix(newPrefix: Prefix): TreeElement
}

typealias Tree = TreeElement? // tree can be empty
typealias KeySlice = Byte // slice of a key for one tree level
typealias Key = List<KeySlice>
typealias Prefix = Key

typealias Content = RowData

// reference to a tree can be either literal element or its hash
class TreeRef(val element: TreeElement?, val hash: String?): Serializable

fun refFromElement(element: TreeElement?): TreeRef = TreeRef(element, null)
fun refFromHash(hash: String): TreeRef = TreeRef(null, hash)

class TLeaf(prefix: Prefix, val content: Content): TreeElement(prefix), Serializable {
    override fun hash(): String {
        var sum = prefix.toByteArray() + content.toHash()
        return GtvByteArray(sum).merkleHash(merkleHashCalculator).toHex()
    }

    override fun cloneWithNewPrefix(newPrefix: Prefix): TreeElement {
        return TLeaf(newPrefix, content)
    }
}


class TNode(prefix: Prefix, val elms: List<TreeRef?>): TreeElement(prefix), Serializable {
    override fun hash(): String {
        var hashes = listOf<String>()
        for (idx in 0 until eltsPerNode) {
            hashes = when (val elt = elms[idx]) {
                null -> hashes.plus(GtvNull.merkleHash(merkleHashCalculator).toHex())
                else ->
                    if (elt.hash != null) {
                        hashes.plus(elt.hash)
                    } else {
                        when (elt.element) {
                            null -> hashes.plus(GtvNull.merkleHash(merkleHashCalculator).toHex())
                            else -> hashes.plus(elt.element.hash())
                        }
                    }
            }
        }
        val hs = hashes.map { hash -> hash.toByteArray() }
        var sum = prefix.toByteArray()
        for (h in hs) {
            sum += h
        }
        return GtvByteArray(sum).merkleHash(merkleHashCalculator).toHex()
    }

    override fun cloneWithNewPrefix(newPrefix: Prefix): TreeElement {
        return TNode(newPrefix, elms)
    }
}

fun keySliceToInt(ks: KeySlice): Int = ks.toInt()

fun toByte(l: List<Boolean>): Byte {
    var s = 0
    for (i in l) {
        s = s shl 1
        if (i) s += 1
    }
    return s.toByte()
}

fun longToKey(l: Long): Key {
    var j = l
    val p = mutableListOf<Boolean>()
    for (bit in 0 until bitsPerKey) {
        p.add(j % 2 == 1L)
        j = j shr 1
    }
    return p.reversed().chunked(bitsPerLevel).map( ::toByte )
}

fun keyToLong(k: Key): Long {
    var s = 0L
    for (ks in k) {
        s = s shl bitsPerLevel
        s += ks
    }
    return s
}

fun findCommonPrefixLength(trees: List<TreeElement>): Int {
    require(trees.size > 1)
    var commonPrefixLength = 0
    while (true) {
        if (trees[0].prefix.size == commonPrefixLength) break
        val ks = trees[0].prefix[commonPrefixLength]
        var done = false
        for (i in 1 until trees.size) {
            if ((trees[i].prefix.size == commonPrefixLength)
                    || (trees[i].prefix[commonPrefixLength] != ks))
            {
                done = true
                break
            }
        }
        if (done) break
        commonPrefixLength++
    }
    return commonPrefixLength
}

fun mergeTrees (trees: List<TreeElement>): Tree {
    if (trees.isEmpty()) return null
    if (trees.size == 1) return trees[0]
    val commonPrefixLength = findCommonPrefixLength(trees)

    // since we have at least 2 elements to merge, we will build a node

    // we have a bin of elements for every element in a node
    val eltBins = Array<MutableList<TreeElement>>(eltsPerNode) { mutableListOf()}

    for (tree in trees) {
        if (tree is TNode && tree.prefix.size == commonPrefixLength) {
            // a node at the level of commonPrefixLenght must be merged, i.e. its elts are emitted into bins
            for (i in 0 until eltsPerNode) {
                val elt = tree.elms[i]
                if (elt != null) {
                    eltBins[i].add(elt.element!!)
                }
            }
        } else {
            // we need to 'chop' the common prefix out of prefix
            // also we need to remove the slice we are currently at
            require(tree.prefix.size > commonPrefixLength) // this could be violated if we have leafs with same key
            val eltIndex = keySliceToInt(tree.prefix[commonPrefixLength])
            eltBins[eltIndex].add( tree.cloneWithNewPrefix(tree.prefix.drop(commonPrefixLength + 1)))
        }
    }
    return TNode(
            trees[0].prefix.take(commonPrefixLength),
            eltBins.map {
                val e = mergeTrees(it)
                if (e != null)
                    refFromElement(e)
                else null
            }
    )
}

fun mergeHashTrees (trees: List<TreeElement>): Tree {
    if (trees.isEmpty()) return null
    if (trees.size == 1) return trees[0]
    val commonPrefixLength = findCommonPrefixLength(trees)

    // since we have at least 2 elements to merge, we will build a node

    // we have a bin of elements for every element in a node
    val eltBins = Array<MutableList<TreeElement>>(eltsPerNode) { mutableListOf()}

    for (tree in trees) {
        if (tree is TNode && tree.prefix.size == commonPrefixLength) {
            // a node at the level of commonPrefixLenght must be merged, i.e. its elts are emitted into bins
            for (i in 0 until eltsPerNode) {
                val elt = tree.elms[i]
                if (elt != null) {
                    eltBins[i].add(elt.element!!)
                }
            }
        } else {
            // we need to 'chop' the common prefix out of prefix
            // also we need to remove the slice we are currently at
            require(tree.prefix.size > commonPrefixLength) // this could be violated if we have leafs with same key
            val eltIndex = keySliceToInt(tree.prefix[commonPrefixLength])
            eltBins[eltIndex].add( tree.cloneWithNewPrefix(tree.prefix.drop(commonPrefixLength + 1)))
        }
    }
    return TNode(
            trees[0].prefix.take(commonPrefixLength),
            eltBins.map {
                val e = mergeHashTrees(it)
                if (e != null)
                    refFromHash(e.hash())
                else null
            }
    )
}


fun sameKeySlice(s1: KeySlice, s2: KeySlice): Boolean {
    return s1 == s2
}

interface ChunkAccess {
    fun getChunk(offset: Long, size: Int): List<TLeaf>
    fun nextKey(from: Long): Key?
}

fun commonKeyPrefix(k1: Key, k2: Key): Int {
    assert(k1.size == k2.size)
    for (i in k1.indices) {
        if (!sameKeySlice(k1[i], k2[i])) return i
    }
    return k1.size
}

fun adjustChunkTree (tree: TreeElement, thisChunkKey: Key, prevChunkKey: Key?, nextChunkKey: Key?): Pair<TreeElement, TreeElement> {
    val maxPrefixSize = (bitsPerKey / bitsPerLevel) - levelsInChunk
    var prefixSize = maxPrefixSize

    fun updatePrefixSize(commonPrefixSize: Int) {
        prefixSize = min(
                prefixSize,
                max(
                        maxPrefixSize - 1 - commonPrefixSize,
                        0
                )
        )
    }

    if (prevChunkKey != null) {
        updatePrefixSize(commonKeyPrefix(thisChunkKey, prevChunkKey))
    }
    if (nextChunkKey != null) {
        updatePrefixSize(commonKeyPrefix(thisChunkKey, nextChunkKey))
    }

    val outerPrefixSize = maxPrefixSize - prefixSize // the part of prefix which is NOT node prefix

    return if (outerPrefixSize == 0)
        Pair(tree, tree) // doesn't need adjustment
    else {
        val nodeIndex = keySliceToInt(tree.prefix[outerPrefixSize - 1])
        val adjustedNode = tree.cloneWithNewPrefix(tree.prefix.drop(outerPrefixSize) )// node with truncated prefix
        // create temporary intermediate node
        Pair(adjustedNode, TNode(tree.prefix.take(outerPrefixSize - 1),
                Array(eltsPerNode
                ) {
                    if (it == nodeIndex)
                        refFromElement(adjustedNode)
                    else
                        null
                }.toList()))
    }
}

fun buildChunked (chunkAccess: ChunkAccess): Tree {
    var offset = 0L
    val size = 1 shl (levelsInChunk * bitsPerLevel)
    var prevChunkKey: Key? = null

    val chunks = mutableListOf<TreeElement>()
    while (chunkAccess.nextKey(offset) != null) {
        val chunkLeafs = chunkAccess.getChunk(offset, size)
        val tree = mergeTrees(chunkLeafs)
        if (tree != null) {
            val nextKey = chunkAccess.nextKey(offset + size)?.dropLast(levelsInChunk)
            val thisChunkKey = longToKey(offset).dropLast(levelsInChunk)
            println("Chunk tree")
            printTree(tree)
            println("-------")
            val chunk = adjustChunkTree(tree, thisChunkKey, prevChunkKey, nextKey)
            println("Chunk at $offset has prefix ${prefixToString(chunk.first.prefix)}")
            if (chunk.first != chunk.second)
                println("using intermediate node with prefix ${prefixToString(chunk.second.prefix)}")
            printTree(chunk.second)
            println("-------")
            // NOTE: chunk.first is the chunk node itself, it is what should be written to disk
            chunks.add(chunk.second)
            prevChunkKey = thisChunkKey
        }
        offset += size
    }

    return mergeTrees(chunks)
}


// list of leafs must be sorted
class SimpleChunkAccess(private val leafs: List<TLeaf>): ChunkAccess {
    override fun getChunk(offset: Long, size: Int): List<TLeaf> {
        return leafs.filter {
            val kl = keyToLong(it.prefix)
            (offset <= kl) && (kl < offset + size)
        }
    }

    override fun nextKey(from: Long): Key? {
        // assume leafs are sorted
        for (l in leafs) {
            if (keyToLong(l.prefix) >= from) return l.prefix
        }
        return null
    }
}

fun byteToBitString (b: Byte): String {
    var s = ""
    var bm : Int = b.toInt()
    for (bi in 0 until bitsPerLevel) {
        s += if (bm % 2 == 0) "0" else "1"
        bm = bm shr 1
    }
    return s.reversed()
}

fun prefixToString(p: Prefix): String {
    return p.joinToString("_", transform = ::byteToBitString)
}

fun printTree(node: Tree, prefix: String = "") {
    val desc = when (node) {
        null -> "null"
        is TLeaf -> "Leaf " + prefixToString(node.prefix) + " " + node.content + " " + node.hash()
        is TNode -> "Node " + prefixToString(node.prefix) + " " + node.hash()
    }
    println(prefix + desc)
    if (node is TNode) {
        for (idx in 0 until eltsPerNode) {
            val elt = node.elms[idx]
            val nextPrefix = "$prefix ${byteToBitString(idx.toByte())} "
            if (elt != null) {
                if (elt.element == null) {
                    println(elt.hash)
                } else {
                    printTree(elt.element, nextPrefix)
                }

            } else {
                println("$nextPrefix#")
            }
        }
    }
}
