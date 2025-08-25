package net.postchain.base.snapshot

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import net.postchain.base.BaseBlockEContext
import net.postchain.base.BaseTxEContext
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.runStorageCommand
import net.postchain.common.BlockchainRid
import net.postchain.common.data.EMPTY_HASH
import net.postchain.common.data.Hash
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.wrap
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.gtx.GTXTransactionFactory
import net.postchain.gtx.GtxBuilder
import net.postchain.gtx.GtxNop
import net.postchain.gtx.StandardOpsGTXModule
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.math.BigInteger

class EventMultiProofTest : SnapshotBaseIT() {

    private val keypair = KeyPairHelper.keyPair(0)

    private data class Setup(val db: DatabaseAccess, val txCtx: BaseTxEContext, val event: EventPageStore)

    private fun withEventStore(test: (Setup) -> Unit) {
        runStorageCommand(appConfig, 0L) { ctx ->
            val db = DatabaseAccess.of(ctx).apply {
                initializeBlockchain(ctx, BlockchainRid.ZERO_RID)
                createPageTable(ctx, "${PREFIX}_event")
                createEventLeafTable(ctx, PREFIX)
            }
            val blockIid = db.insertBlock(ctx, 1)
            val bctx = BaseBlockEContext(ctx, 0, blockIid, 10, mapOf(), mock())
            val signers = listOf(keypair.pubKey.data)
            val gtxData = GtxBuilder(BlockchainRid.ZERO_RID, signers, cs, GtvMerkleHashCalculatorV2(cs))
                    .addOperation(GtxNop.OP_NAME, GtvFactory.gtv(42))
                    .finish().sign(cs.buildSigMaker(keypair)).buildGtx().encode()
            val tx = GTXTransactionFactory(BlockchainRid.ZERO_RID, StandardOpsGTXModule(), cs, GtvMerkleHashCalculatorV2(cs))
                    .decodeTransaction(gtxData)
            val txIid = db.insertTransaction(bctx, tx, 1)
            val txEContext = BaseTxEContext(bctx, txIid, tx)
            val event = EventPageStore(bctx, levelsPerPage, ds, PREFIX)
            test(Setup(db, txEContext, event))
        }
    }

    private fun buildAndWrite(eventSetup: Setup, blockHeight: Long, count: Int): List<Hash> {
        val (db, txCtx, _) = eventSetup
        val leafs = arrayListOf<Hash>()
        for (i in 0 until count) {
            val data = BigInteger.valueOf(i.toLong() + 1).toByteArray()
            val hash = ds.digest(data)
            db.insertEvent(txCtx, PREFIX, blockHeight, i.toLong(), hash, data)
            leafs.add(hash)
        }
        eventSetup.event.writeEventTree(blockHeight, leafs)
        return leafs
    }

    @Test
    fun rangeProofIsOptimalAndCorrect() {
        withEventStore { setup ->
            val blockHeight = 1L
            val leafs = buildAndWrite(setup, blockHeight, 32)
            val start = 3L
            val end = 9L

            val rangeProof = setup.event.getMerkleProof(blockHeight, start, end)

            // Verify we have the expected left boundary proofs
            assertThat(rangeProof.leftBoundaryHashes).hasSize(2)
            assertThat(rangeProof.leftBoundaryHashes[0].wrap()).isEqualTo(leafs[2].wrap())
            assertThat(rangeProof.leftBoundaryHashes[1].wrap()).isEqualTo(ds.hash(leafs[0], leafs[1]).wrap())

            // Verify we have the expected right boundary proofs
            assertThat(rangeProof.rightBoundaryHashes).hasSize(2)
            assertThat(rangeProof.rightBoundaryHashes[0].wrap()).isEqualTo(ds.hash(leafs[10], leafs[11]).wrap())
            assertThat(rangeProof.rightBoundaryHashes[1].wrap()).isEqualTo(ds.hash(
                    ds.hash(leafs[12], leafs[13]),
                    ds.hash(leafs[14], leafs[15]),
            ).wrap())

            // Test 2: Verify the range proof can reconstruct the correct root
            val root = setup.event.writeEventTree(blockHeight, leafs)
            val match = verifyRangeProof(root, rangeProof, start, leafs.subList(3, 10))
            assertThat(match).isTrue()
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
