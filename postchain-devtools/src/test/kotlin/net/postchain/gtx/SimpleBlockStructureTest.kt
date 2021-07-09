// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx

import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.configurations.GTXTestModule
import net.postchain.core.BlockchainRid
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.testinfra.TestOneOpGtxTransaction
import org.junit.Assert
import org.junit.Test

/**
 * Goal: to produce a block containing GTX transactions using the simplest possible setup
 *
 * Idea of this test is to test that transactions actually end up inside the block, and also test that
 * we get the correct special TX in there.
 */
class SimpleBlockStructureTest : IntegrationTestSetup() {

    private lateinit var gtxTxFactory: GTXTransactionFactory

    val chainId = 1L // We only have one.

    private fun tx(id: Int): GTXTransaction {
        return TestOneOpGtxTransaction(gtxTxFactory, id).getGTXTransaction()
    }

    @Test(timeout = 2 * 60 * 1000L)
    fun testBlockContent() {
        val count = 3
        configOverrides.setProperty("testpeerinfos", createPeerInfos(count))
        configOverrides.setProperty("api.port", 0)
        createNodes(count, "/net/postchain/devtools/gtx/blockchain_config_simple_3node_gtx.xml")

        // --------------------
        // Needed to create TXs
        // --------------------
        val blockchainRID: BlockchainRid = nodes[0].getBlockchainInstance().getEngine().getConfiguration().blockchainRid
        val module = GTXTestModule()
        val cs = SECP256K1CryptoSystem()
        gtxTxFactory = GTXTransactionFactory(blockchainRID, module, cs)


        // --------------------
        // Create TXs
        // --------------------
        var currentHeight = 0L
        buildBlockNoWait(nodes, chainId, currentHeight, tx(0), tx(1), tx(2))
        awaitHeight(chainId, currentHeight)

        currentHeight++
        buildBlockNoWait(nodes, chainId, currentHeight, tx(3), tx(4), tx(5))
        awaitHeight(chainId, currentHeight)

        currentHeight++
        buildBlockNoWait(nodes, chainId, currentHeight, tx(6), tx(7), tx(8))
        awaitHeight(chainId, currentHeight)

        // --------------------
        // Actual test
        // --------------------
        val experctedNumberOfTxs = 3 + 2

        val bockQueries = nodes[0].getBlockchainInstance().getEngine().getBlockQueries()
        for (i in 0..2) {
            val blockData = bockQueries.getBlockAtHeight(i.toLong()).get()!!
            Assert.assertEquals(experctedNumberOfTxs, blockData.transactions.size)


            // Verify we also have the mandatory Special transactions
            System.out.println("block $i fetched.")
            // Check if the block holds any Special Transactions
            for (tx in blockData.transactions) {
                val txGtx = gtxTxFactory.decodeTransaction(tx) as GTXTransaction
                System.out.println("Tx found, nr of operations: ${txGtx.ops.size}")
                for (op in txGtx.ops) {
                    System.out.println("Op : ${op.toString()}")
                }
            }
        }
    }
}