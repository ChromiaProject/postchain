// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.integrationtest

import net.postchain.core.BlockchainRid
import net.postchain.configurations.GTXTestModule
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.KeyPairHelper
import net.postchain.devtools.testinfra.TestTransaction
import net.postchain.gtv.GtvFactory
import net.postchain.gtx.GTXDataBuilder
import net.postchain.gtx.GTXTransaction
import net.postchain.gtx.GTXTransactionFactory
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BlockchainConfigurationTest : IntegrationTestSetup() {

    @Test
    fun testMaxBlockSize() {
        val nodes = createNodes(1, "/net/postchain/devtools/blocks/blockchain_config_max_block_size.xml")
        val node = nodes[0]
        val engine = node.getBlockchainInstance().blockchainEngine

        // blockchain_config_max_block_size.xml was set maxblocksize is 150 bytes and maxtransaction is 4
        // the size of testtransaction is 40 bytes
        // so we send 4 transactions (160bytes) which is over maxblocksize. Cnce we committed block, we expect 3 transactions inserted.
        for (i in 1..4) {
            engine.getTransactionQueue().enqueue(TestTransaction(i))
        }
        // reason why we need to try catch is when block committed is over block size,
        // it throws exception and could stop the test case, so the asserting was not reached.
        try {
           buildBlockAndCommit(node)
        }  catch (e : Exception) {

        }

        // we need to sleep a bit (1s) to let the block committed accepted transactions.
        Thread.sleep(1000)

        val height = getBestHeight(node)
        val acceptedTxs = getTxRidsAtHeight(node, height)
        assertEquals(3, acceptedTxs.size)
    }

    @Test
    fun testMaxTransactionSize() {
        val blockchainRid = BlockchainRid.buildFromHex("63E5DE3CBE247D4A57DE19EF751F7840431D680DEC1EC9023B8986E7ECC35412")
        configOverrides.setProperty("infrastructure", "base/test")
        val nodes = createNodes(1, "/net/postchain/devtools/blocks/blockchain_config_max_transaction_size.xml")
        val node = nodes[0]
        val txQueue = node.getBlockchainInstance().blockchainEngine.getTransactionQueue()

        // over 2mb
        txQueue.enqueue(buildTransaction(blockchainRid, "${RandomStringUtils.randomAlphanumeric(1024 * 1024 * 2)}-test"))
        try {
            buildBlockAndCommit(node)
        } catch (e: Exception) {
        }

        assertEquals(-1, getBestHeight(node))

        // less than 2mb
        txQueue.enqueue(buildTransaction(blockchainRid, "${RandomStringUtils.randomAlphanumeric(1024 * 1024)}"))
        buildBlockAndCommit(node)
        assertEquals(0, getBestHeight(node))
    }

    private fun buildTransaction(blockchainRid: BlockchainRid, value: String): GTXTransaction {
        val builder = GTXDataBuilder(blockchainRid, arrayOf(KeyPairHelper.pubKey(0)), cryptoSystem)
        val factory = GTXTransactionFactory(blockchainRid, GTXTestModule(), cryptoSystem)
        builder.addOperation("gtx_test", arrayOf(GtvFactory.gtv(1L), GtvFactory.gtv(value)))
        builder.finish()
        builder.sign(cryptoSystem.buildSigMaker(KeyPairHelper.pubKey(0), KeyPairHelper.privKey(0)))

        return factory.build(builder.getGTXTransactionData())
    }
}