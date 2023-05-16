// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.integrationtest

import assertk.assertions.isEqualTo
import assertk.isContentEqualTo
import net.postchain.common.BlockchainRid
import net.postchain.configurations.GTXTestModule
import net.postchain.crypto.KeyPair
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.testinfra.TestTransaction
import net.postchain.gtv.GtvFactory
import net.postchain.gtx.GTXTransaction
import net.postchain.gtx.GTXTransactionFactory
import net.postchain.gtx.GtxBuilder
import org.apache.commons.lang3.RandomStringUtils
import org.awaitility.Awaitility
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class BlockchainConfigurationTest : IntegrationTestSetup() {

    @Test
    fun testMaxBlockSize() {
        val nodes = createNodes(1, "/net/postchain/devtools/blocks/blockchain_config_max_block_size.xml")
        val node = nodes[0]
        val engine = node.getBlockchainInstance().blockchainEngine

        // blockchain_config_max_block_size.xml was set maxblocksize is 150 bytes and maxtransaction is 4
        // the size of TestTransaction is 40 bytes,
        // so we send 4 transactions (160bytes) which is over maxblocksize. Once we committed block, we expect 3 transactions inserted.
        for (i in 1..4) {
            engine.getTransactionQueue().enqueue(TestTransaction(i))
        }

        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted {
            val height = getLastHeight(node)
            val acceptedTxs = getTxRidsAtHeight(node, height)
            assertEquals(3, acceptedTxs.size)
        }
    }

    @Test
    fun testMaxTransactionSize() {
        val blockchainRid = BlockchainRid.buildFromHex("C988399D8295F8AD8CA92EFB8C926308356961980D5E717FE5978CC8AC8C1B20")
        val nodes = createNodes(2, "/net/postchain/devtools/blocks/blockchain_config_max_transaction_size.xml")

        // over 2mb
        val largeTx = buildTransaction(blockchainRid, "${RandomStringUtils.randomAlphanumeric(1024 * 1024 * 2)}-test")
        buildBlockNoWait(listOf(nodes[0]), 1L, 0, largeTx)

        // less than 2mb
        val okTx = buildTransaction(blockchainRid, RandomStringUtils.randomAlphanumeric(1024 * 1024))
        buildBlockNoWait(listOf(nodes[1]), 1L, 0, okTx)
        awaitHeight(1L, 0)

        // node0 will try to build block but node1 will reject it due to tx being too big
        // node1 will instead build first block with the OK tx
        nodes.forEach {
            val txsInBlock = getTxRidsAtHeight(it, 0)
            assertk.assert(txsInBlock.size).isEqualTo(1)
            assertk.assert(txsInBlock[0]).isContentEqualTo(okTx.getRID())
        }
    }

    private fun buildTransaction(blockchainRid: BlockchainRid, value: String): GTXTransaction {
        val factory = GTXTransactionFactory(blockchainRid, GTXTestModule(), cryptoSystem)
        val builder = GtxBuilder(blockchainRid, listOf(KeyPairHelper.pubKey(0)), cryptoSystem)
                .addOperation("gtx_test", GtvFactory.gtv(1L), GtvFactory.gtv(value))
                .finish()
                .sign(cryptoSystem.buildSigMaker(KeyPair(KeyPairHelper.pubKey(0), KeyPairHelper.privKey(0))))

        return factory.build(builder.buildGtx())
    }
}