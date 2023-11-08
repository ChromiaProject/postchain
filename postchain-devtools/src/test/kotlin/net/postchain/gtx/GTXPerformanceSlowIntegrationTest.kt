// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx

import mu.KLogging
import net.postchain.common.BlockchainRid
import net.postchain.configurations.GTXTestModule
import net.postchain.crypto.KeyPair
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.devtools.KeyPairHelper.privKey
import net.postchain.crypto.devtools.KeyPairHelper.pubKey
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.ebft.worker.ValidatorBlockchainProcess
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvFactory.gtv
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.system.measureNanoTime

/**
 * This test gives you an idea about the performance of different parts of transaction processing pipeline.
 * If you're wondering what the node is doing and how long specific parts take, you can start with this test
 * to get a rough idea of what is "normal".
 *
 * Numbers below are produced on a 4-core Core i7 running on a laptop, a pretty good but not excellent CPU.
 *
 * To get a number, look for an output saying e.g. "Time elapse: 626 ms", then divide by 1000 (number of transactions).
 * (Note: printed output, not the number reported by test harness.)
 * It is strongly recommended to run test several times in one go, which can be done in IntelliJ run configuration.
 * First run will be slow due to slow start, so ignore it.
 *
 * makeTx - build 1000 transactions, one gtx_test operation each, including signing and encoding.
 * 0.4 ms per transaction
 *
 * parseTx - parse 1000 transactions, one gtx_test operation each.
 * 0.014 ms per transaction
 *
 * parseTxVerify - parse and verify 1000 transactions, one gtx_test operation each.
 * 0.2 ms per transaction. (This suggests that signature verification is the most expensive part of transaction ingestion.)
 *
 * runXNodesWithYTxPerBlockBuildOnly - build X nodes, and process Y transactions per block, for 2 blocks.
 * The third parameter indicates mode:
 *  * mode 0 - do not include transaction decoding in time
 *  * mode 1 - include transaction decoding in time
 *
 * 1 node, 1000 tx per block, mode 0: 390 ms per block
 * It's also worth looking for a log line saying something like:
 * Block is finalized: 365 ms, 2758 net tps, 2737 gross tps, delay: 0 ms, height: 1, accepted txs: 1000
 *
 * The difference between "Time elapsed" number and "Block is finalized" number is the time spent on committing a block.
 * Thus, this test also gives you an idea how long it takes to commit a block, for example.
 *
 * Numbers from different runs suggest it would take 0.25 to 0.35 ms to append a single transaction on this machine.
 *
 * 1 node, 1000 tx per block, mode 1: 590 ms per block
 * This is pretty straightforward, as it takes 200 ms to parse and verify 1000 transactions.
 * Enqueueing overhead seems to be negligible.
 *
 * 4 nodes, 1000 tx per block, mode 0: 900 ms per block
 * Here's what's going on:
 *
 * 1. Node 0 builds a block, which takes 350 ms.
 * 2. Block is received by the other nodes, which start loading block in parallel.
 * 3. This parallel processing takes longer, ~500 ms, as nodes compete for resources of a single CPU.
 * 4. Eventually block is finalized, communication overhead is on the scale of 50 ms.
 *
 * 4 nodes, 1000 tx per block, mode 1: 1.3 s per block
 *
 * Main takeaways:
 * * Baseline transaction processing time is 0.3 ms, or 0.5 ms if you include decoding/verification time.
 *      (Which should be included if you are constrained to a single CPU, but would not delay block building if you have a plenty of CPU.)
 * * Maximum theoretical tps for Postchain architecture is on scale of 1000-1500 tps. I.e. going beyond that would
 *   require significant architectural changes.
 * * A corollary to this is that if you get something below 1000 tps, then either
 *     * application is doing something heavier than inserting a single row
 *     * or nodes are slow.
 *
 **/


class GTXPerformanceSlowIntegrationTest : IntegrationTestSetup() {

    companion object : KLogging()

    val dummyBcRid = BlockchainRid.buildFromHex("ABABAABABABABABABABABABABABABABAABABABABABABABABABABABABABAABABA")

    val sigMaker = net.postchain.devtools.gtx.myCS.buildSigMaker(KeyPair(pubKey(0), privKey(0)))

    private fun makeTestTx(id: Long, value: String, blockchainRid: BlockchainRid): ByteArray {
        val b = GtxBuilder(blockchainRid, listOf(pubKey(0)), net.postchain.devtools.gtx.myCS)
                .addOperation("gtx_test", gtv(id), gtv(value))
                .finish()
                .sign(sigMaker)
                .buildGtx()
        return b.encode()
    }

    @Test
    fun makeTx() {
        var total = 0
        val nanoDelta = measureNanoTime {
            for (i in 0..999) {
                total += makeTestTx(i.toLong(), "Hello", dummyBcRid).size
            }
        }
        assertTrue(total > 1000)
        println("Time elapsed: ${nanoDelta / 1000000} ms")
    }

    @Test
    fun parseTxData() {
        val transactions = (0..999).map {
            makeTestTx(it.toLong(), it.toString(), dummyBcRid)
        }
        var total = 0
        val nanoDelta = measureNanoTime {
            for (rawTx in transactions) {
                val gtvData = GtvFactory.decodeGtv(rawTx)
                val gtxData = Gtx.fromGtv(gtvData)
                total += gtxData.gtxBody.operations.size
            }
        }
        assertTrue(total == 1000)
        println("Time elapsed: ${nanoDelta / 1000000} ms")
    }

    @Test
    fun parseTx() {
        val transactions = (0..999).map {
            makeTestTx(it.toLong(), it.toString(), dummyBcRid)
        }
        var total = 0
        val module = GTXTestModule()
        val cs = Secp256K1CryptoSystem()
        val txFactory = GTXTransactionFactory(dummyBcRid, module, cs)
        val nanoDelta = measureNanoTime {
            for (rawTx in transactions) {
                val ttx = txFactory.decodeTransaction(rawTx) as GTXTransaction
                total += ttx.ops.size
            }
        }
        assertTrue(total == 1000)
        println("Time elapsed: ${nanoDelta / 1000000} ms")
    }

    @Test
    fun parseTxVerify() {
        val transactions = (0..999).map {
            makeTestTx(1, it.toString(), dummyBcRid)
        }
        var total = 0
        val module = GTXTestModule()
        val cs = Secp256K1CryptoSystem()
        val txFactory = GTXTransactionFactory(dummyBcRid, module, cs)
        val nanoDelta = measureNanoTime {
            for (rawTx in transactions) {
                val ttx = txFactory.decodeTransaction(rawTx) as GTXTransaction
                total += ttx.ops.size
                ttx.checkCorrectness()
            }
        }
        assertTrue(total == 1000)
        println("Time elapsed: ${nanoDelta / 1000000} ms")
    }

    @ParameterizedTest
    @CsvSource(
            "1, 1000, 0", "1, 1000, 1",
            "4, 1000, 0", "4, 1000, 1"
    )
    fun runXNodesWithYTxPerBlockBuildOnly(nodeCount: Int, txPerBlock: Int, mode: Int) {
        val blocksCount = 2
        configOverrides.setProperty("testpeerinfos", createPeerInfos(nodeCount))
        val nodes = createNodes(nodeCount, "/net/postchain/devtools/performance/blockchain_config_$nodeCount.xml")
        val blockchainRid = systemSetup.blockchainMap[1]!!.rid

        var txId = 0
        val statusManager = (nodes[0].getBlockchainInstance() as ValidatorBlockchainProcess).statusManager
        for (i in 0 until blocksCount) {
            val txs = (1..txPerBlock).map { makeTestTx(1, (txId++).toString(), blockchainRid) }

            val engine = nodes[statusManager.primaryIndex()]
                    .getBlockchainInstance()
                    .blockchainEngine
            val txFactory = engine
                    .getConfiguration()
                    .getTransactionFactory()
            val queue = engine.getTransactionQueue()

            val nanoDelta = if (mode == 0) {
                txs.forEach {
                    queue.enqueue(txFactory.decodeTransaction(it))
                }
                measureNanoTime {
                    nodes.forEach { strategy(it).buildBlocksUpTo(i.toLong()) }
                    nodes.forEach { strategy(it).awaitCommitted(i) }
                }
            } else {
                measureNanoTime {
                    txs.forEach { queue.enqueue(txFactory.decodeTransaction(it)) }
                    nodes.forEach { strategy(it).buildBlocksUpTo(i.toLong()) }
                    nodes.forEach { strategy(it).awaitCommitted(i) }
                }
            }

            println("Time elapsed: ${nanoDelta / 1000000} ms")
        }
    }

}