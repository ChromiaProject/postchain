package net.postchain.base.snapshot

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import net.postchain.base.BaseBlockEContext
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.runStorageCommand
import net.postchain.common.BlockchainRid
import net.postchain.common.data.EMPTY_HASH
import net.postchain.common.data.Hash
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.wrap
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.math.BigInteger
import java.util.TreeMap

class SnapshotRangeProofTest : SnapshotBaseIT() {

    private fun withSnapshotStore(test: (SnapshotPageStore) -> Unit) {
        runStorageCommand(appConfig, 0L) { ctx ->
            val db = DatabaseAccess.of(ctx).apply {
                initializeBlockchain(ctx, BlockchainRid.ZERO_RID)
                createPageTable(ctx, "${PREFIX}_snapshot")
            }
            val blockIid = db.insertBlock(ctx, 1)
            val bctx = BaseBlockEContext(ctx, 0, blockIid, 10, mapOf(), mock())
            val snapshotStore = SnapshotPageStore(bctx, levelsPerPage, 0, ds, PREFIX)
            test(snapshotStore)
        }
    }

    private fun buildAndWrite(snapshotStore: SnapshotPageStore, blockHeight: Long, count: Int): TreeMap<Long, Hash> {
        val leafs = TreeMap<Long, Hash>()
        for (i in 0 until count) {
            val data = BigInteger.valueOf(i.toLong() + 1).toByteArray()
            val hash = ds.digest(data)
            leafs[i.toLong()] = hash
        }
        snapshotStore.updateSnapshot(blockHeight, leafs, 2)
        return leafs
    }

    @Test
    fun rangeProofIsCorrect() {
        withSnapshotStore { snapshotStore ->
            val blockHeight = 1L
            val leafs = buildAndWrite(snapshotStore, blockHeight, 32)
            val start = 3L
            val end = 9L

            val rangeProof = snapshotStore.getMerkleProof(blockHeight, start, end)

            // Verify we have the expected left boundary proofs
            assertThat(rangeProof.leftBoundaryHashes).hasSize(2)
            assertThat(rangeProof.leftBoundaryHashes[0].wrap()).isEqualTo(leafs[2]!!.wrap())
            assertThat(rangeProof.leftBoundaryHashes[1].wrap()).isEqualTo(ds.hash(leafs[0]!!, leafs[1]!!).wrap())

            // Verify we have the expected right boundary proofs
            assertThat(rangeProof.rightBoundaryHashes).hasSize(2)
            assertThat(rangeProof.rightBoundaryHashes[0].wrap()).isEqualTo(ds.hash(leafs[10]!!, leafs[11]!!).wrap())
            assertThat(rangeProof.rightBoundaryHashes[1].wrap()).isEqualTo(ds.hash(
                    ds.hash(leafs[12]!!, leafs[13]!!),
                    ds.hash(leafs[14]!!, leafs[15]!!),
            ).wrap())

            // Test 2: Verify the range proof can reconstruct the correct root
            val root = snapshotStore.updateSnapshot(blockHeight, leafs, 2)
            val match = verifyRangeProof(root, rangeProof, start, leafs.subMap(3L, 10L).values.toList())
            assertThat(match).isTrue()
        }
    }

    @Test
    fun rangeProofEdgeCases() {
        withSnapshotStore { snapshotStore ->
            val blockHeight = 1L
            val leafs = buildAndWrite(snapshotStore, blockHeight, 32)
            val root = snapshotStore.updateSnapshot(blockHeight, leafs, 2)

            // One leaf node
            val rangeProofOneNode = snapshotStore.getMerkleProof(blockHeight, 4, 4)
            assertThat(verifyRangeProof(root, rangeProofOneNode, 4, leafs.subMap(4L, 5L).values.toList())).isTrue()

            // Two adjacent leaf nodes
            val rangeProofTwoAdjacent = snapshotStore.getMerkleProof(blockHeight, 4, 5)
            assertThat(verifyRangeProof(root, rangeProofTwoAdjacent, 4, leafs.subMap(4L, 6L).values.toList())).isTrue()

            // Two non-adjacent leaf nodes
            val rangeProofTwoNonAdjacent = snapshotStore.getMerkleProof(blockHeight, 5, 6)
            assertThat(verifyRangeProof(root, rangeProofTwoNonAdjacent, 5, leafs.subMap(5L, 7L).values.toList())).isTrue()// Two non-adjacent leaf nodes

            // Range is the whole tree
            val rangeProofWholeTree = snapshotStore.getMerkleProof(blockHeight, 0, 31)
            assertThat(verifyRangeProof(root, rangeProofWholeTree, 0, leafs.values.toList())).isTrue()

            val rangeProofTwoAdjacentRight = snapshotStore.getMerkleProof(blockHeight, 28, 29)
            assertThat(verifyRangeProof(root, rangeProofTwoAdjacentRight, 28, leafs.subMap(28L, 30L).values.toList())).isTrue()

            val rangeProofTwoNonAdjacentRight = snapshotStore.getMerkleProof(blockHeight, 27, 28)
            assertThat(verifyRangeProof(root, rangeProofTwoNonAdjacentRight, 27, leafs.subMap(27L, 29L).values.toList())).isTrue()

            val rangeProofSpanningNonExisting = snapshotStore.getMerkleProof(blockHeight, 30, 35)
            val emptyLeafs = List(3) { EMPTY_HASH }
            assertThat(verifyRangeProof(root, rangeProofSpanningNonExisting, 30, leafs.subMap(30L, 33L).values.toList() + emptyLeafs)).isTrue()
        }
    }

    @Test
    fun testEmptyProof() {
        withSnapshotStore { snapshotStore ->
            val blockHeight = 1L
            val leafs = buildAndWrite(snapshotStore, blockHeight, 64)
            val root = snapshotStore.updateSnapshot(blockHeight, leafs, 2)

            // This is a complete tree without any padding with empty hashes so proof will be completely empty
            val rangeProofWholeTree = snapshotStore.getMerkleProof(blockHeight, 0, 63)
            assertThat(rangeProofWholeTree.commonPath).isEmpty()
            assertThat(rangeProofWholeTree.leftBoundaryHashes).isEmpty()
            assertThat(rangeProofWholeTree.rightBoundaryHashes).isEmpty()
            assertThat(verifyRangeProof(root, rangeProofWholeTree, 0, leafs.values.toList())).isTrue()
        }
    }

    private fun verifyRangeProof(expectedRoot: Hash, proof: RangeProof, startLeafIndex: Long, leafHashes: List<Hash>): Boolean {
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
            var levelStart = currentStart
            var levelEnd = currentEnd
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
                ds.hash(result,  commonHash)  // We're left child
            } else {
                ds.hash(commonHash, result)  // We're right child
            }
            nodeIndex = nodeIndex / 2
        }

        return result.contentEquals(expectedRoot)
    }
}
