// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools.gtx

import net.postchain.common.BlockchainRid
import net.postchain.common.toHex
import net.postchain.concurrent.util.get
import net.postchain.core.Transaction
import net.postchain.crypto.KeyPair
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.devtools.KeyPairHelper.privKey
import net.postchain.crypto.devtools.KeyPairHelper.pubKey
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtx.GtxBuilder
import net.postchain.gtx.GtxNop
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

val myCS = Secp256K1CryptoSystem()

class GTXIntegrationTest : IntegrationTestSetup() {

    fun makeNOPGTX(bcRid: BlockchainRid): ByteArray {
        val b = GtxBuilder(bcRid, listOf(pubKey(0)), myCS)
                .addOperation(GtxNop.OP_NAME, gtv(42))
                .finish()
                .sign(myCS.buildSigMaker(KeyPair(pubKey(0), privKey(0))))
                .buildGtx()
        return b.encode()
    }

    fun makeTestTx(id: Long, value: String, bcRid: BlockchainRid): ByteArray {
        val b = GtxBuilder(bcRid, listOf(pubKey(0)), myCS)
                .addOperation("gtx_test", gtv(id), gtv(value))
                .finish()
                .sign(myCS.buildSigMaker(KeyPair(pubKey(0), privKey(0))))
                .buildGtx()
        return b.encode()
    }

    fun makeTimeBTx(from: Long, to: Long?, bcRid: BlockchainRid): ByteArray {
        val b = GtxBuilder(bcRid, listOf(pubKey(0)), myCS)
                .addOperation("timeb",
                        gtv(from),
                        if (to != null) gtv(to) else GtvNull
                )
                // Need to add a valid dummy operation to make the entire TX valid
                .addOperation("gtx_test", gtv(1), gtv("true"))
                .finish()
                .sign(myCS.buildSigMaker(KeyPair(pubKey(0), privKey(0))))
                .buildGtx()
        return b.encode()
    }

    @Test
    fun testBuildBlock() {
        configOverrides.setProperty("infrastructure", "base/test")
        val nodes = createNodes(1, "/net/postchain/devtools/gtx_it/blockchain_config.xml")
        val node = nodes[0]
        val bcRid = systemSetup.blockchainMap[1]!!.rid // Just assume we have chain 1

        fun enqueueTx(data: ByteArray): Transaction? {
            try {
                val tx = node.getBlockchainInstance().blockchainEngine.getConfiguration().getTransactionFactory().decodeTransaction(data)
                node.getBlockchainInstance().blockchainEngine.getTransactionQueue().enqueue(tx)
                return tx
            } catch (e: Exception) {
                logger.error(e) { "Can't enqueue tx" }
            }
            return null
        }

        val validTxs = mutableListOf<Transaction>()
        var currentBlockHeight = -1L

        fun makeSureBlockIsBuiltCorrectly() {
            currentBlockHeight += 1
            buildBlockAndCommit(node.getBlockchainInstance().blockchainEngine)
            assertEquals(currentBlockHeight, getLastHeight(node))
            val ridsAtHeight = getTxRidsAtHeight(node, currentBlockHeight)
            for (vtx in validTxs) {
                val vtxRID = vtx.getRID()
                assertTrue(ridsAtHeight.any { it.contentEquals(vtxRID) })
            }
            assertEquals(validTxs.size, ridsAtHeight.size)
            validTxs.clear()
        }

        // Tx1 valid)
        val validTx1 = enqueueTx(makeTestTx(1, "true", bcRid))!!
        validTxs.add(validTx1)

        // Tx 2 invalid, b/c bad args
        enqueueTx(makeTestTx(2, "false", bcRid))!!

        // Tx 3: Nop (invalid, since need more ops)
        val x = makeNOPGTX(bcRid)
        enqueueTx(x)

        // -------------------------
        // Build it
        // -------------------------
        makeSureBlockIsBuiltCorrectly()

        // Tx 4: time, valid, no stop is ok
        val tx4Time = makeTimeBTx(0, null, bcRid)
        validTxs.add(enqueueTx(tx4Time)!!)

        // Tx 5: time, valid, from beginning of time to now
        val tx5Time = makeTimeBTx(0, System.currentTimeMillis(), bcRid)
        validTxs.add(enqueueTx(tx5Time)!!)

        // TX 6: time, invalid since from bigger than to
        val tx6Time = makeTimeBTx(100, 0, bcRid)
        enqueueTx(tx6Time)

        // TX 7: time, invalid since from is in the future
        val tx7Time = makeTimeBTx(System.currentTimeMillis() + 100, null, bcRid)
        enqueueTx(tx7Time)

        // -------------------------
        // Build it
        // -------------------------
        makeSureBlockIsBuiltCorrectly()

        val value = node.getBlockchainInstance().blockchainEngine.getBlockQueries().query(
                "gtx_test_get_value",
                gtv(mapOf(
                        "txRID" to gtv(validTx1.getRID().toHex())
                ))
        )
        assertEquals(gtv("true"), value.get())
    }
}