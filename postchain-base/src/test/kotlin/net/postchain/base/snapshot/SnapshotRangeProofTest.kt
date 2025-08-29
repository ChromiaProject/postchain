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
import net.postchain.common.exception.UserMistake
import net.postchain.common.wrap
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import java.math.BigInteger
import java.util.TreeMap

class SnapshotRangeProofTest : SnapshotBaseIT() {

    private fun withSnapshotStore(levelsPerPageToTest: List<Int> = listOf(2), test: (SnapshotPageStore) -> Unit) {
        for (levelsPerPage in levelsPerPageToTest) {
            runStorageCommand(appConfig, 0L, wipeDatabase = true) { ctx ->
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
        withSnapshotStore(listOf(1, 2, 3, 4)) { snapshotStore ->
            val blockHeight = 1L
            val leafs = buildAndWrite(snapshotStore, blockHeight, 32)
            val start = 3L
            val end = 9L

            val rangeProof = snapshotStore.getRangeMerkleProof(blockHeight, start, end)

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
            val match = VerifyRangeProof(ds).verify(root, rangeProof, start, leafs.subMap(3L, 10L).values.toList())
            assertThat(match).isTrue()
        }
    }

    @Test
    fun rangeProofEdgeCases() {
        withSnapshotStore(listOf(1, 2, 3, 4)) { snapshotStore ->
            val blockHeight = 1L
            val leafs = buildAndWrite(snapshotStore, blockHeight, 32)
            val root = snapshotStore.updateSnapshot(blockHeight, leafs, 2)

            // One leaf node
            val rangeProofOneNode = snapshotStore.getRangeMerkleProof(blockHeight, 4, 4)
            assertThat(VerifyRangeProof(ds).verify(root, rangeProofOneNode, 4, leafs.subMap(4L, 5L).values.toList())).isTrue()

            // Two adjacent leaf nodes
            val rangeProofTwoAdjacent = snapshotStore.getRangeMerkleProof(blockHeight, 4, 5)
            assertThat(VerifyRangeProof(ds).verify(root, rangeProofTwoAdjacent, 4, leafs.subMap(4L, 6L).values.toList())).isTrue()

            // Two non-adjacent leaf nodes
            val rangeProofTwoNonAdjacent = snapshotStore.getRangeMerkleProof(blockHeight, 5, 6)
            assertThat(VerifyRangeProof(ds).verify(root, rangeProofTwoNonAdjacent, 5, leafs.subMap(5L, 7L).values.toList())).isTrue()// Two non-adjacent leaf nodes

            // Range is the whole tree
            val rangeProofWholeTree = snapshotStore.getRangeMerkleProof(blockHeight, 0, 31)
            assertThat(VerifyRangeProof(ds).verify(root, rangeProofWholeTree, 0, leafs.values.toList())).isTrue()

            val rangeProofTwoAdjacentRight = snapshotStore.getRangeMerkleProof(blockHeight, 28, 29)
            assertThat(VerifyRangeProof(ds).verify(root, rangeProofTwoAdjacentRight, 28, leafs.subMap(28L, 30L).values.toList())).isTrue()

            val rangeProofTwoNonAdjacentRight = snapshotStore.getRangeMerkleProof(blockHeight, 27, 28)
            assertThat(VerifyRangeProof(ds).verify(root, rangeProofTwoNonAdjacentRight, 27, leafs.subMap(27L, 29L).values.toList())).isTrue()
        }
    }

    @Test
    fun nonExistingRangeProof() {
        // Can't test with levels per page = 1 here b/c such a tree has a different padding at root level.
        // This limitation exists in single leaf proof as well
        // We just verify here that in the cases where we still can give a valid proof we get one and that otherwise
        // we get an exception
        withSnapshotStore(listOf(2, 3, 4)) { snapshotStore ->
            val blockHeight = 1L
            val leafs = buildAndWrite(snapshotStore, blockHeight, 32)
            val root = snapshotStore.updateSnapshot(blockHeight, leafs, 2)

            val rangeProofSpanningNonExisting = snapshotStore.getRangeMerkleProof(blockHeight, 30, 35)
            val emptyLeafs = List(3) { EMPTY_HASH }
            assertThat(VerifyRangeProof(ds).verify(root, rangeProofSpanningNonExisting, 30, leafs.subMap(30L, 33L).values.toList() + emptyLeafs)).isTrue()
        }

        withSnapshotStore(listOf(1)) { snapshotStore ->
            val blockHeight = 1L

            assertThrows<UserMistake> {
                snapshotStore.getRangeMerkleProof(blockHeight, 30, 35)
            }
        }
    }

    @Test
    fun testEmptyProof() {
        withSnapshotStore { snapshotStore ->
            val blockHeight = 1L
            val leafs = buildAndWrite(snapshotStore, blockHeight, 64)
            val root = snapshotStore.updateSnapshot(blockHeight, leafs, 2)

            // This is a complete tree without any padding with empty hashes, so the proof will be completely empty (obviously only if levelsPerPage is 2 like in this test)
            val rangeProofWholeTree = snapshotStore.getRangeMerkleProof(blockHeight, 0, 63)
            assertThat(rangeProofWholeTree.commonPath).isEmpty()
            assertThat(rangeProofWholeTree.leftBoundaryHashes).isEmpty()
            assertThat(rangeProofWholeTree.rightBoundaryHashes).isEmpty()
            assertThat(VerifyRangeProof(ds).verify(root, rangeProofWholeTree, 0, leafs.values.toList())).isTrue()
        }
    }
}
