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

class GTXPerformanceTestNightly : IntegrationTestSetup() {

    companion object : KLogging()

    val dummyBcRid = BlockchainRid.buildFromHex("ABABAABABABABABABABABABABABABABAABABABABABABABABABABABABABAABABA")

    private fun makeTestTx(id: Long, value: String, blockchainRid: BlockchainRid): ByteArray {
        val b = GtxBuilder(blockchainRid, listOf(pubKey(0)), net.postchain.devtools.gtx.myCS)
                .addOperation("gtx_test", gtv(id), gtv(value))
                .finish()
                .sign(net.postchain.devtools.gtx.myCS.buildSigMaker(KeyPair(pubKey(0), privKey(0))))
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