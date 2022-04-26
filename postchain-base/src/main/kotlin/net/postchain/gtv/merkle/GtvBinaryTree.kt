// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtv.merkle

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.merkle.GtvMerkleBasics.HASH_PREFIX_NODE_GTV_ARRAY
import net.postchain.gtv.merkle.GtvMerkleBasics.HASH_PREFIX_NODE_GTV_DICT
import net.postchain.gtv.merkle.path.PathElement

/**
 * In this file we handle the most common case, where the binary tree holds only [Gtv] s.
 *
 * When we calculate the merkle root hash, we need get different hashes if 2 trees have same leaf elements,
 * but different internal structure. Therefore we use special [GtvArrayHeadNode] and [GtvDictHeadNode] to
 * signal this difference.
 */

/**
 * Represents the top of a sub tree generated by a [GtvArray]
 *
 * @property left (see super)
 * @property right (see super)
 * @property content (see super)
 * @property size is how many element we have in the original array.
 * @property sizeInBytes is how many bytes the original array takes up.
 * @property pathElem (see super)
 */
class GtvArrayHeadNode(left: BinaryTreeElement, right: BinaryTreeElement, content: Gtv, val size: Int, val sizeInBytes: Int, pathElem: PathElement? = null):
        SubTreeRootNode<Gtv>(left, right, content, pathElem) {

    init {
        if (content !is GtvArray) {
            throw IllegalStateException("How come we use this array type when the type is not an GtvArray?")
        }
    }

    override fun getPrefixByte(): Byte = HASH_PREFIX_NODE_GTV_ARRAY

    override fun getNrOfBytes(): Int = sizeInBytes
}

/**
 * Represents the top a sub tree generated by a [GtvDictionary]
 *
 * @property left (see super)
 * @property right (see super)
 * @property content (see super)
 * @property size is how many key-pairs we have in the original dict.
 * @property sizeInBytes is how many bytes the original dict takes up.
 * @property pathElem (see super)
 */
class GtvDictHeadNode(left: BinaryTreeElement, right: BinaryTreeElement, content: Gtv, val size: Int, val sizeInBytes: Int, pathElem: PathElement? = null):
        SubTreeRootNode<Gtv>(left, right, content, pathElem){

    init {
        if (content !is GtvDictionary) {
            throw IllegalStateException("How come we use this dict type when the type is not an GtvDictionary?")
        }
    }

    override fun getPrefixByte(): Byte = HASH_PREFIX_NODE_GTV_DICT

    override fun getNrOfBytes(): Int = sizeInBytes
}

/**
 * Represents a [BinaryTree] that only holds Gtv values.
 *
 * @property root is the root of the (whole) tree generated form the original [Gtv]
 */
class GtvBinaryTree(root: BinaryTreeElement) : BinaryTree<Gtv>(root)

