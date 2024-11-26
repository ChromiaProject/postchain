package net.postchain.base.snapshot

import net.postchain.base.BaseBlockEContext
import net.postchain.base.BaseTxEContext
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.runStorageCommand
import net.postchain.common.BlockchainRid
import net.postchain.common.data.Hash
import net.postchain.common.toHex
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.gtv.GtvFactory
import net.postchain.gtx.GTXTransactionFactory
import net.postchain.gtx.GtxBuilder
import net.postchain.gtx.GtxNop
import net.postchain.gtx.StandardOpsGTXModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.math.BigInteger

class EventTest : SnapshotBaseIT() {

    private val keypair = KeyPairHelper.keyPair(0)

    @Test
    fun testEventTree() {
        runStorageCommand(appConfig, 0L) { ctx ->
            val db = DatabaseAccess.of(ctx).apply {
                initializeBlockchain(ctx, BlockchainRid.ZERO_RID)
                createPageTable(ctx, "${PREFIX}_event")
                createEventLeafTable(ctx, PREFIX)
            }
            val blockIid = db.insertBlock(ctx, 1)
            val bctx = BaseBlockEContext(ctx, 0, blockIid, 10, mapOf(), mock())
            val signers = listOf(keypair.pubKey.data)
            val gtxData = GtxBuilder(BlockchainRid.ZERO_RID, signers, cs)
                    .addOperation(GtxNop.OP_NAME, GtvFactory.gtv(42))
                    .finish().sign(cs.buildSigMaker(keypair)).buildGtx().encode()
            val tx = GTXTransactionFactory(BlockchainRid.ZERO_RID, StandardOpsGTXModule(), cs)
                    .decodeTransaction(gtxData)
            val txIid = db.insertTransaction(bctx, tx, 1)
            val txEContext = BaseTxEContext(bctx, txIid, tx)
            val event = EventPageStore(bctx, levelsPerPage, ds, PREFIX)

            val blockHeight = 1L
            val leafs = arrayListOf<Hash>()
            for (i in 1..128) {
                val data = BigInteger.valueOf(i.toLong()).toByteArray()
                val hash = ds.digest(data)
                db.insertEvent(txEContext, PREFIX, blockHeight, leafs.size.toLong(), hash, data)
                leafs.add(hash)
            }

            leafs.forEach { hash ->
                val eventInfo = db.getEvent(ctx, PREFIX, hash)
                assertEquals(hash.toHex(), eventInfo!!.hash.toHex())
            }

            val root = event.writeEventTree(blockHeight, leafs)
            arrayOf(0, 1, 36, 88, 126, 127).forEach {
                val proofs = event.getMerkleProof(blockHeight, it.toLong())
                val expected = calculateMerkleRoot(proofs, it.toLong(), leafs[it])
                assertEquals(expected.toHex(), root.toHex())
            }

            // Add event on next height and assert that levels from previous height is not included in proof
            val nextHeight = blockHeight + 1
            val data = BigInteger.valueOf(129L).toByteArray()
            val hash = ds.digest(data)
            db.insertEvent(txEContext, PREFIX, nextHeight, 0, hash, data)
            val nextHeightRoot = event.writeEventTree(nextHeight, listOf(hash))

            val proofs = event.getMerkleProof(nextHeight, 0)
            val expected = calculateMerkleRoot(proofs, 0, hash)
            assertEquals(expected.toHex(), nextHeightRoot.toHex())
        }
    }
}