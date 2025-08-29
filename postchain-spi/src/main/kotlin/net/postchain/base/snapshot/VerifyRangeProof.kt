package net.postchain.base.snapshot

import net.postchain.common.data.EMPTY_HASH
import net.postchain.common.data.Hash
import net.postchain.common.exception.ProgrammerMistake

class VerifyRangeProof(private val ds: DigestSystem) {

    fun verify(expectedRoot: Hash, proof: RangeProof, startLeafIndex: Long, leafHashes: List<Hash>): Boolean {
        if (leafHashes.isEmpty()) throw ProgrammerMistake("Must have at least one leaf")

        if (leafHashes.size == 1) {
            // Single leaf case - use standard Merkle proof verification
            val merkleRoot = calculateMerkleRoot(proof.commonPath, startLeafIndex, leafHashes[0])
            return merkleRoot.contentEquals(expectedRoot)
        }

        // Extract the range of leaves from the full leaf array
        val endLeafIndex = startLeafIndex + leafHashes.size - 1
        val rangeLeaves = leafHashes.subList(0, leafHashes.size)

        // Build up the tree level by level
        var currentLevel = rangeLeaves.toMutableList()
        var currentStart = startLeafIndex
        var currentEnd = endLeafIndex
        var leftBoundaryIndex = 0
        var rightBoundaryIndex = 0

        // Keep building up until we have a single node (convergence point)
        while (currentLevel.size > 1 || currentStart != currentEnd) {
            val nextLevel = mutableListOf<Hash>()
            val levelStart = currentStart
            val levelEnd = currentEnd
            var nodeIndex = 0

            // Process all nodes at this level
            while (nodeIndex < currentLevel.size) {
                val currentNodePos = levelStart + nodeIndex

                if (currentNodePos % 2 == 1L) {
                    // This node is a right child, need left sibling from boundary proof
                    val leftSibling = if (leftBoundaryIndex < proof.leftBoundaryHashes.size) {
                        proof.leftBoundaryHashes[leftBoundaryIndex++]
                    } else EMPTY_HASH

                    val parentHash = ds.hash(leftSibling, currentLevel[nodeIndex])
                    nextLevel.add(parentHash)
                    nodeIndex += 1
                } else if (nodeIndex + 1 < currentLevel.size) {
                    // We have both left and right children in our range
                    val leftHash = currentLevel[nodeIndex]
                    val rightHash = currentLevel[nodeIndex + 1]
                    val parentHash = ds.hash(leftHash, rightHash)
                    nextLevel.add(parentHash)
                    nodeIndex += 2
                } else {
                    // This node is a left child, need right sibling from boundary proof
                    val rightSibling = if (rightBoundaryIndex < proof.rightBoundaryHashes.size) {
                        proof.rightBoundaryHashes[rightBoundaryIndex++]
                    } else EMPTY_HASH

                    val parentHash = ds.hash(currentLevel[nodeIndex], rightSibling)
                    nextLevel.add(parentHash)
                    nodeIndex += 1
                }
            }

            // Move to next level
            currentLevel = nextLevel
            currentStart = levelStart / 2
            currentEnd = levelEnd / 2
        }

        // Now we should have exactly one node - follow the common path to root
        if (currentLevel.size != 1) return false

        var result = currentLevel[0]
        var nodeIndex = currentStart

        // Apply the common path hashes
        for (commonHash in proof.commonPath) {
            result = if (nodeIndex % 2 == 0L) {
                ds.hash(result, commonHash)  // We're left child
            } else {
                ds.hash(commonHash, result)  // We're right child
            }
            nodeIndex /= 2
        }

        return result.contentEquals(expectedRoot)
    }

    fun calculateMerkleRoot(proofs: List<Hash>, pos: Long, leaf: Hash): Hash {
        var r = leaf
        proofs.forEachIndexed { i, h ->
            r = if (((pos shr i) and 1) != 0L) {
                ds.hash(h, r)
            } else {
                ds.hash(r, h)
            }
        }
        return r
    }

    /**
     * @param allLeafs leafs in the tree
     * @param levelsPerPage levels per page that is used in the page tree, this is necessary to know if we need to pad
     * with empty hashes at the end of the tree.
     */
    fun calculateMerkleRoot(allLeafs: List<Hash>, levelsPerPage: Int): Hash {
        if (allLeafs.isEmpty()) return EMPTY_HASH

        // Calculate the number of leaves needed to fill the tree
        var leavesNeeded = 1 shl levelsPerPage
        while (leavesNeeded < allLeafs.size) {
            leavesNeeded = leavesNeeded shl levelsPerPage
        }

        // Create mutable list with actual leaves
        var currentLevel = allLeafs.toMutableList()

        // Pad with empty hashes if needed
        while (currentLevel.size < leavesNeeded) {
            currentLevel.add(EMPTY_HASH)
        }

        // Keep merging pairs of hashes until we have a single hash (the root)
        while (currentLevel.size > 1) {
            val nextLevel = mutableListOf<Hash>()

            // Process pairs of nodes
            var i = 0
            while (i < currentLevel.size) {
                nextLevel.add(ds.hash(currentLevel[i],currentLevel[i + 1]))
                i += 2
            }
        
            currentLevel = nextLevel
        }
    
        return currentLevel[0]
    }
}
