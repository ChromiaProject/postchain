package net.postchain.base.merkle

import net.postchain.gtx.*
import java.util.SortedSet

/**
 * In this file we handle the most common case, where the binary tree holds only [GTXValue] s.
 *
 * When we calculate the merkle root hash, we need get different hashes if 2 trees have same leaf elements,
 * but different internal structure. Therefore we use special [GtxArrayHeadNode] and [GtxDictHeadNode] to
 * signal this difference.
 */

/**
 * Represents the top of a sub tree generated by a [ArrayGTXValue]
 */
class GtxArrayHeadNode(left: BinaryTreeElement, right: BinaryTreeElement, isProofLeaf: Boolean, content: GTXValue): SubTreeRootNode<GTXValue>(left, right, isProofLeaf, content) {

    companion object GtxArrayNodeCompanion{
        const val prefixByte: Byte = 7
    }

    override fun getPrefixByte(): Byte = prefixByte
}

/**
 * Represents the top a sub tree generated by a [DictGTXValue]
 */
class GtxDictHeadNode(left: BinaryTreeElement, right: BinaryTreeElement, isProofLeaf: Boolean, content: GTXValue): SubTreeRootNode<GTXValue>(left, right, isProofLeaf, content){

    companion object GtxDictNodeCompanion{
        const val prefixByte: Byte = 8
    }

    override fun getPrefixByte(): Byte = prefixByte
}

/**
 * Represents a [BinaryTree] that only holds GTX values
 */
class GtxBinaryTree(root: BinaryTreeElement) : BinaryTree<GTXValue>(root) {


}

/**
 * This can build two types of trees:
 * 1. Make a binary tree out of a GTX object graph
 * 2. Same as above, but we also marked each GTX sub structure that should be a path leaf.
 *    If you want this option (2) you have to provide a list of [GTXPath]
 */
class GtxFullBinaryTreeFactory : BinaryTreeFactory<GTXValue, GTXPath>() {


    /**
     * Generic builder.
     * @param gtxValue will take any damn thing
     */
    fun buildFromGtx(gtxValue: GTXValue): GtxBinaryTree {
        return buildFromGtxAndPath(gtxValue, GTXPath.NO_PATHS)
    }

    /**
     * Generic builder.
     * @param gtxValue will take any damn thing
     * @param gtxPathList will tell us what element that are path leafs
     */
    fun buildFromGtxAndPath(gtxValue: GTXValue, gtxPathList: List<GTXPath>): GtxBinaryTree {
        val result = handleLeaf(gtxValue, gtxPathList)
        return GtxBinaryTree(result)
    }

    // ------------ Internal ----------


    /**
     * There are 2 edge cases here:
     * - When the array is empty. -> We return a top node with two empty leafs
     * - When there is only one element. -> We set the right element as empty
     */
    private fun buildFromArrayGTXValue(arrayGTXValue: ArrayGTXValue, gtxPathList: List<GTXPath>): BinaryTreeElement {
        val isThisAProofLeaf = gtxPathList.any{ it.isAtLeaf() } // Will tell us if any of the paths points to this element
        //println("Arr,(is proof? $isThisAProofLeaf) Proof path (size: ${gtxPathList.size} ) list: " + GTXPath.debugRerpresentation(gtxPathList))

        val leafList: List<GTXValue> = arrayGTXValue.array.map {it}
        if (leafList.isEmpty()) {
            return GtxArrayHeadNode(EmptyLeaf, EmptyLeaf, isThisAProofLeaf, arrayGTXValue)
        }

        val leafArray = arrayListOf<BinaryTreeElement>()

        // 1. Build first (leaf) layer
        for (i in 0..(leafList.size - 1)) {
            val pathsRelevantForThisLeaf = GTXPath.getTailIfFirstElementIsArrayOfThisIndexFromList(i, gtxPathList)
            //println("New paths, (size: ${pathsRelevantForThisLeaf.size} ) list: " + GTXPath.debugRerpresentation(pathsRelevantForThisLeaf))
            val leaf = leafList[i]
            val locbtElement = handleLeaf(leaf, pathsRelevantForThisLeaf)
            leafArray.add(locbtElement)
        }

        // 2. Build all higher layers
        val result = buildHigherLayer(1, leafArray)

        // 3. Fix and return the root node
        val orgRoot = result.get(0)
        return when (orgRoot) {
            is Node -> {
                GtxArrayHeadNode(orgRoot.left, orgRoot.right, isThisAProofLeaf, arrayGTXValue)
            }
            is Leaf<*> -> {
                if (leafList.size > 1) {
                    throw IllegalStateException("How come we got a leaf returned when we had ${leafList.size} elements is the array?")
                } else {
                    // Create a dummy to the right
                    GtxArrayHeadNode(orgRoot, EmptyLeaf, isThisAProofLeaf, arrayGTXValue)
                }
            }
            else -> throw IllegalStateException("Should not find element of this type here: $orgRoot")
        }
    }

    /**
     * The strategy for transforming [DictGTXValue] is pretty simple, we treat the key and value as leafs and
     * add them both as elements to the tree.
     *
     * There is an edge cases here:
     * - When the dict is empty. -> We return a top node with two empty leafs
     */
    private fun buildFromDictGTXValue(dictGTXValue: DictGTXValue, gtxPathList: List<GTXPath>): GtxDictHeadNode {
        val isThisAProofLeaf = gtxPathList.any{ it.isAtLeaf() } // Will tell us if any of the paths points to this element
        //println("Dict,(is proof? $isThisAProofLeaf) Proof path (size: ${gtxPathList.size} ) list: " + GTXPath.debugRerpresentation(gtxPathList))
        val keys: SortedSet<String> = dictGTXValue.dict.keys.toSortedSet() // Needs to be sorted, or else the order is undefined

        if (keys.isEmpty()) {
            return GtxDictHeadNode(EmptyLeaf, EmptyLeaf, isThisAProofLeaf, dictGTXValue)
        }

        val leafArray = arrayListOf<BinaryTreeElement>()

        // 1. Build first (leaf) layer
        for (key in keys) {

            //println("key extracted: $key")

            // 1.a Fix the key
            val keyGtxString: GTXValue = StringGTXValue(key)
            val keyElement = handleLeaf(keyGtxString, GTXPath.NO_PATHS) // The key cannot not be proved, so NO_PATHS
            leafArray.add(keyElement)

            // 1.b Fix the value/content
            val pathsRelevantForThisLeaf = GTXPath.getTailIfFirstElementIsDictOfThisKeyFromList(key, gtxPathList)
            val content: GTXValue = dictGTXValue.get(key)!!  // TODO: Is it ok to bang here if the dict is broken?
            val contentElement = handleLeaf(content, pathsRelevantForThisLeaf)
            leafArray.add(contentElement)
        }

        // 2. Build all higher layers
        val result = buildHigherLayer(1, leafArray)

        // 3. Fix and return the root node
        val orgRoot = result.get(0)
        return when (orgRoot) {
            is Node -> GtxDictHeadNode(orgRoot.left, orgRoot.right, isThisAProofLeaf, dictGTXValue)
            else -> throw IllegalStateException("Should not find element of this type here: $orgRoot")
        }
    }


    /**
     * Handles different types of [GTXValue] values
     */
    override fun handleLeaf(leaf: GTXValue, gtxPathList: List<GTXPath>): BinaryTreeElement {
        //println("handleLeaf, Proof path (size: ${gtxPathList.size} ) list: " + GTXPath.debugRerpresentation(gtxPathList))
        return when (leaf) {
            is ArrayGTXValue -> buildFromArrayGTXValue(leaf, gtxPathList)
            is DictGTXValue -> buildFromDictGTXValue(leaf, gtxPathList)
            else -> {
                val isThisAProofLeaf = gtxPathList.any{ it.isAtLeaf() } // Will tell us if any of the paths points to this element
                //println("GTX leaf, proof? $isThisAProofLeaf")
                Leaf(leaf, isThisAProofLeaf)
            }
        }
    }
}