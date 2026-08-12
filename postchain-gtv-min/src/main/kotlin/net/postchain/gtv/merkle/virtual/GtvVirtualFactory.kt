package net.postchain.gtv.merkle.virtual

import mu.KLogging
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvTypeException
import net.postchain.gtv.GtvVirtual
import net.postchain.gtv.GtvVirtualArray
import net.postchain.gtv.GtvVirtualDictionary
import net.postchain.gtv.merkle.path.ArrayGtvPathElement
import net.postchain.gtv.merkle.path.DictGtvPathElement
import net.postchain.gtv.merkle.path.SearchableGtvPathElement
import net.postchain.gtv.merkle.proof.GtvMerkleProofTree
import net.postchain.gtv.merkle.proof.MerkleProofElement
import net.postchain.gtv.merkle.proof.ProofHashedLeaf
import net.postchain.gtv.merkle.proof.ProofNode
import net.postchain.gtv.merkle.proof.ProofNodeGtvArrayHead
import net.postchain.gtv.merkle.proof.ProofNodeGtvDictHead
import net.postchain.gtv.merkle.proof.ProofValueGtvLeaf
import net.postchain.gtv.merkle.proof.ProofValueLeaf

/**
 * Builds [GtvVirtual] structures out of proof trees.
 */
public object GtvVirtualFactory : KLogging() {

    /**
     * Note: the corner case where the proof covers the entire structure is not handled — the result would be
     * the original object anyway.
     *
     * @return a [GtvVirtual] that corresponds to the original [Gtv]
     */
    public fun buildGtvVirtual(proofTree: GtvMerkleProofTree): GtvVirtual = when (val root = proofTree.root) {
        is ProofNodeGtvArrayHead -> buildGtvVirtualArray(root, true)
        is ProofNodeGtvDictHead -> buildGtvVirtualDictionary(root, true)
        is ProofNode -> throw GtvTypeException("A proof structure cannot have an (internal) node as root.")
        is ProofHashedLeaf -> throw GtvTypeException("A proof structure cannot have a hash as root.")
        is ProofValueLeaf<*> -> throw GtvTypeException(
            "A proof structure cannot be just the value that should be proven (meaningless)."
        )

        else -> throw GtvTypeException("We don't handle proofs that begin with type: $root")
    }

    // --------------------- Array ----------------------

    /**
     * Transforms an entire binary tree structure that came from a [GtvArray] back into a [GtvVirtualArray].
     *
     * [ProofNode]s are meaningless, because they didn't exist in the original array. [ProofHashedLeaf]s are also
     * of no interest, since we don't know what the original value was.
     *
     * @param isRoot is true if this is the top element (the top element does not have a path elem)
     */
    public fun buildGtvVirtualArray(arrHeadElement: ProofNodeGtvArrayHead, isRoot: Boolean = false): GtvVirtualArray {
        val tmpSet = if (isRoot) {
            handleArrLeftAndRight(arrHeadElement.left, arrHeadElement.right)
        } else {
            buildGtvVirtualArrayInner(arrHeadElement)
        }
        return tmpSet.buildGtvVirtualArray(arrHeadElement, arrHeadElement.size)
    }

    private fun handleArrLeftAndRight(left: MerkleProofElement, right: MerkleProofElement): ArrayIndexAndGtvList {
        val virtualLeft = buildGtvVirtualArrayInner(left)
        virtualLeft.addAll(buildGtvVirtualArrayInner(right))
        return virtualLeft
    }

    private fun getIndex(pathElem: SearchableGtvPathElement) = (pathElem as ArrayGtvPathElement).index

    private fun buildGtvVirtualArrayInner(currentElement: MerkleProofElement): ArrayIndexAndGtvList =
        when (currentElement) {
            // Empty leaf (we don't care about these)
            is ProofHashedLeaf -> ArrayIndexAndGtvList()

            // Valuable leaf (a leaf that holds a real Gtv value)
            is ProofValueGtvLeaf -> ArrayIndexAndGtvList(getIndex(currentElement.pathElem), currentElement.content)

            // Valuable leaf (the beginning of a new dict-tree)
            is ProofNodeGtvDictHead -> {
                val index = getIndex(currentElement.pathElem!!)
                val tmpMap = handleLDictLeftAndRight(currentElement.left, currentElement.right)
                ArrayIndexAndGtvList(index, GtvVirtualDictionary(currentElement, tmpMap, currentElement.size))
            }

            // Valuable leaf (the beginning of a new array-tree)
            is ProofNodeGtvArrayHead -> {
                val index = getIndex(currentElement.pathElem!!)
                val tmpSet = handleArrLeftAndRight(currentElement.left, currentElement.right)
                ArrayIndexAndGtvList(index, tmpSet.buildGtvVirtualArray(currentElement, currentElement.size))
            }

            // Meaningless intermediary (does not represent anything in the GtvVirtual)
            is ProofNode -> handleArrLeftAndRight(currentElement.left, currentElement.right)

            else -> throw IllegalStateException("Should have handled this type: $currentElement")
        }

    // --------------------- Dictionary ----------------------

    /**
     * Transforms an entire binary tree structure that came from a [GtvDictionary] back into a
     * [GtvVirtualDictionary].
     *
     * @param isRoot is true if this is the top element (the top element does not have a path elem)
     */
    public fun buildGtvVirtualDictionary(
        dictHeadElement: ProofNodeGtvDictHead,
        isRoot: Boolean = false,
    ): GtvVirtualDictionary {
        val tmpMap = if (isRoot) {
            handleLDictLeftAndRight(dictHeadElement.left, dictHeadElement.right)
        } else {
            buildGtvVirtualDictionaryInner(dictHeadElement)
        }
        return GtvVirtualDictionary(dictHeadElement, tmpMap, dictHeadElement.size)
    }

    private fun handleLDictLeftAndRight(
        left: MerkleProofElement,
        right: MerkleProofElement,
    ): MutableMap<String, Gtv> {
        val leftMap = buildGtvVirtualDictionaryInner(left)
        leftMap.putAll(buildGtvVirtualDictionaryInner(right))
        return leftMap
    }

    private fun buildGtvVirtualDictionaryInner(currentElement: MerkleProofElement): MutableMap<String, Gtv> =
        when (currentElement) {
            is ProofHashedLeaf -> mutableMapOf()

            is ProofValueGtvLeaf ->
                mutableMapOf((currentElement.pathElem as DictGtvPathElement).key to currentElement.content)

            is ProofNodeGtvDictHead -> {
                val key = (currentElement.pathElem as DictGtvPathElement).key
                val dictMap = handleLDictLeftAndRight(currentElement.left, currentElement.right)
                mutableMapOf(key to GtvVirtualDictionary(currentElement, dictMap, currentElement.size))
            }

            is ProofNodeGtvArrayHead -> {
                val key = (currentElement.pathElem as DictGtvPathElement).key
                val tmpSet = handleArrLeftAndRight(currentElement.left, currentElement.right)
                mutableMapOf(key to tmpSet.buildGtvVirtualArray(currentElement, currentElement.size))
            }

            is ProofNode -> handleLDictLeftAndRight(currentElement.left, currentElement.right)

            else -> throw IllegalStateException("Should have handled this type: $currentElement")
        }
}
