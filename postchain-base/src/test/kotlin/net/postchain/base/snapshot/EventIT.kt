package net.postchain.base.snapshot

import net.postchain.base.BaseBlockEContext
import net.postchain.base.BaseTxEContext
import net.postchain.base.TxEventSink
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.runStorageCommand
import net.postchain.common.BlockchainRid
import net.postchain.common.data.Hash
import net.postchain.common.toHex
import net.postchain.core.TxEContext
import net.postchain.crypto.KeyPair
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtx.GTXTransaction
import net.postchain.gtx.GTXTransactionFactory
import net.postchain.gtx.GtxBuilder
import net.postchain.gtx.GtxNop
import net.postchain.gtx.StandardOpsGTXModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigInteger

class EventIT : SnapshotBaseIT() {
    private val cs = Secp256K1CryptoSystem()

    @Test
    fun testEventTree() {
        val chainId = 0L

        runStorageCommand(appConfig, chainId) { ctx ->
            val db = DatabaseAccess.of(ctx)
            db.initializeBlockchain(ctx, BlockchainRid.ZERO_RID)
            db.apply {
                createPageTable(ctx, "${PREFIX}_event")
                createEventLeafTable(ctx, PREFIX)
            }
            val dummyEventSink = object : TxEventSink {
                override fun processEmittedEvent(ctxt: TxEContext, type: String, data: Gtv) {
                    return
                }
            }
            val blockIid = db.insertBlock(ctx, 1)
            val bctx = BaseBlockEContext(ctx, 0, blockIid, 10, mapOf(), dummyEventSink)
            val signers = listOf(KeyPairHelper.pubKey(0))
            val sigMaker = cs.buildSigMaker(KeyPair(KeyPairHelper.pubKey(0), KeyPairHelper.privKey(0)))
            val factory = GTXTransactionFactory(BlockchainRid.ZERO_RID, StandardOpsGTXModule(), cs)
            val gtxData = GtxBuilder(BlockchainRid.ZERO_RID, signers, cs)
                    .addOperation(GtxNop.OP_NAME, GtvFactory.gtv(42))
                    .finish().sign(sigMaker).buildGtx().encode()
            val tx = factory.decodeTransaction(gtxData) as GTXTransaction
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
                val expected = getMerkleRoot(proofs, it, leafs[it])
                assertEquals(expected.toHex(), root.toHex())
            }
        }
    }
}