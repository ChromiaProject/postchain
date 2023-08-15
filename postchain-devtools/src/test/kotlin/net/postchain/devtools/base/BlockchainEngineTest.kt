// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools.base

import net.postchain.common.exception.UserMistake
import net.postchain.core.BadDataException
import net.postchain.core.block.BlockBuilder
import net.postchain.core.block.BlockData
import net.postchain.core.block.BlockWitness
import net.postchain.core.block.MultiSigBlockWitnessBuilder
import net.postchain.crypto.KeyPair
import net.postchain.crypto.devtools.KeyPairHelper.privKey
import net.postchain.crypto.devtools.KeyPairHelper.pubKey
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.testinfra.BaseTestInfrastructureFactory
import net.postchain.devtools.testinfra.ErrorTransaction
import net.postchain.devtools.testinfra.TestBlockchainConfiguration
import net.postchain.devtools.testinfra.TestTransaction
import net.postchain.devtools.testinfra.UnexpectedExceptionTransaction
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BlockchainEngineTest : IntegrationTestSetup() {

    @BeforeEach
    fun setTestInfrastructure() {
        configOverrides.setProperty("infrastructure", BaseTestInfrastructureFactory::class.qualifiedName)
    }

    @Test
    fun testBuildBlock() {
        val nodes = createNodes(1, "/net/postchain/devtools/blocks/blockchain_config.xml")
        val node = nodes[0]
        val txQueue = node.getBlockchainInstance().blockchainEngine.getTransactionQueue()

        txQueue.enqueue(TestTransaction(0))
        buildBlockAndCommit(node)
        assertEquals(0, getLastHeight(node))
        val riDsAtHeight0 = getTxRidsAtHeight(node, 0)
        assertEquals(1, riDsAtHeight0.size)
        assertArrayEquals(TestTransaction(id = 0).getRID(), riDsAtHeight0[0])

        txQueue.enqueue(TestTransaction(1))
        txQueue.enqueue(TestTransaction(2))
        buildBlockAndCommit(node)
        assertEquals(1, getLastHeight(node))
        assertTrue(riDsAtHeight0.contentDeepEquals(getTxRidsAtHeight(node, 0)))
        val riDsAtHeight1 = getTxRidsAtHeight(node, 1)
        assertTrue(riDsAtHeight1.contentDeepEquals(Array(2, { TestTransaction(it + 1).getRID() })))

        // Empty block. All tx but last (10) will be failing
        txQueue.enqueue(TestTransaction(3, good = true, correct = false))
        txQueue.enqueue(TestTransaction(4, good = false, correct = true))
        txQueue.enqueue(TestTransaction(5, good = false, correct = false))
        txQueue.enqueue(ErrorTransaction(6, true, true))
        txQueue.enqueue(ErrorTransaction(7, false, true))
        txQueue.enqueue(ErrorTransaction(8, true, false))
        txQueue.enqueue(UnexpectedExceptionTransaction(9))
        txQueue.enqueue(TestTransaction(10))

        buildBlockAndCommit(node)
        assertEquals(2, getLastHeight(node))
        assertTrue(riDsAtHeight1.contentDeepEquals(getTxRidsAtHeight(node, 1)))
        val txRIDsAtHeight2 = getTxRidsAtHeight(node, 2)
        assertEquals(1, txRIDsAtHeight2.size)
        assertArrayEquals(TestTransaction(10).getRID(), txRIDsAtHeight2[0])
    }

    @Test
    fun testLoadUnfinishedEmptyBlock() {
        val (node0, node1) = createNodes(2, "/net/postchain/devtools/blocks/blockchain_config_2.xml")

        val blockData = createBlockWithTxAndCommit(node0, 0)

        loadUnfinishedAndCommit(node1, blockData)
        assertEquals(0, getLastHeight(node1))
        val riDsAtHeight0 = getTxRidsAtHeight(node1, 0)
        assertEquals(0, riDsAtHeight0.size)
    }

    @Test
    fun testLoadUnfinishedBlock2tx() {
        val (node0, node1) = createNodes(2, "/net/postchain/devtools/blocks/blockchain_config_2.xml")

        val blockData = createBlockWithTxAndCommit(node0, 2)
        loadUnfinishedAndCommit(node1, blockData)

        assertEquals(0, getLastHeight(node1))
        val riDsAtHeight0 = getTxRidsAtHeight(node1, 0)
        assertTrue(riDsAtHeight0.contentDeepEquals(Array(2) { TestTransaction(it).getRID() }))
    }

    @Test
    fun testMultipleLoadUnfinishedBlocks() {
        val (node0, node1) = createNodes(2, "/net/postchain/devtools/blocks/blockchain_config_2.xml")

        for (i in 0..10) {
            val blockData = createBlockWithTxAndCommit(node0, 2, i * 2)
            loadUnfinishedAndCommit(node1, blockData)

            assertEquals(i.toLong(), getLastHeight(node1))
            val riDsAtHeighti = getTxRidsAtHeight(node1, i.toLong())
            assertTrue(riDsAtHeighti.contentDeepEquals(Array(2) { TestTransaction(i * 2 + it).getRID() }))
        }
    }

    @Test
    fun testLoadUnfinishedBlockTxFail() {
        val (node0, node1) = createNodes(2, "/net/postchain/devtools/blocks/blockchain_config_2.xml")

        val blockData = createBlockWithTxAndCommit(node0, 2)

        val bc = node1.getBlockchainInstance().blockchainEngine.getConfiguration() as TestBlockchainConfiguration
        // Make the tx invalid on follower. Should discard whole block
        bc.transactionFactory.specialTxs[0] = ErrorTransaction(0, true, false)
        try {
            loadUnfinishedAndCommit(node1, blockData)
            fail()
        } catch (userMistake: UserMistake) {
            // Expected
        }
        // Block must not have been created.
        assertEquals(-1, getLastHeight(node1))

        bc.transactionFactory.specialTxs.clear()
        // And we can create a new valid block afterwards.
        loadUnfinishedAndCommit(node1, blockData)

        assertEquals(0, getLastHeight(node1))
        val riDsAtHeight0 = getTxRidsAtHeight(node1, 0)
        assertTrue(riDsAtHeight0.contentDeepEquals(Array(2) { TestTransaction(it).getRID() }))
    }

    @Test
    fun testLoadUnfinishedBlockInvalidHeader() {
        val (node0, node1) = createNodes(2, "/net/postchain/devtools/blocks/blockchain_config_2.xml")

        val blockData = createBlockWithTxAndCommit(node0, 2)
        blockData.header.prevBlockRID[0]++
        try {
            loadUnfinishedAndCommit(node1, blockData)
            fail()
        } catch (userMistake: BadDataException) {
            // Expected
        }
        // Block must not have been created.
        assertEquals(-1, getLastHeight(node1))
    }

    // an oversized block it doesn't work to produce.
    // To test this, you need to nodes to run different configurations (i.e. tweak node 0 to
    // allow higher limit) OR produce this block manually e.g. just taking a bunch of transactions
    @Test
    fun testMaxBlockTransactions() {
        val nodes = createNodes(1, "/net/postchain/devtools/blocks/blockchain_config_max_block_transaction.xml")
        val node = nodes[0]
        val txQueue = node.getBlockchainInstance().blockchainEngine.getTransactionQueue()

        // send 100 transactions
        for (i in 1..100) {
            txQueue.enqueue(TestTransaction(i))
        }

        // commit 3 times
        buildBlockAndCommit(node) // height 0
        buildBlockAndCommit(node) // height 1
        buildBlockAndCommit(node) // height 2
        val riDsAtHeight0 = getTxRidsAtHeight(node, 0)
        val riDsAtHeight1 = getTxRidsAtHeight(node, 1)
        val riDsAtHeight2 = getTxRidsAtHeight(node, 2)

        // due to we set configuration maxblocktransaciton is 7, so only 7 transactions expected for each committed
        assertEquals(7, riDsAtHeight0.size)
        assertEquals(7, riDsAtHeight1.size)
        assertEquals(7, riDsAtHeight2.size)
    }

    private fun createBlockWithTxAndCommit(node: PostchainTestNode, txCount: Int, startId: Int = 0): BlockData {
        val blockBuilder = createBlockWithTx(node, txCount, startId)
        commitBlock(blockBuilder)
        return blockBuilder.getBlockData()
    }

    private fun createBlockWithTx(node: PostchainTestNode, txCount: Int, startId: Int = 0): BlockBuilder {
        val engine = node.getBlockchainInstance().blockchainEngine
        (startId until startId + txCount).forEach {
            engine.getTransactionQueue().enqueue(TestTransaction(it))
        }
        return engine.buildBlock().first
    }

    private fun loadUnfinishedAndCommit(node: PostchainTestNode, blockData: BlockData) {
        val (blockBuilder, exception) = node.getBlockchainInstance().blockchainEngine.loadUnfinishedBlock(blockData, false)
        if (exception != null) {
            throw exception
        } else {
            commitBlock(blockBuilder)
        }
    }

    private fun commitBlock(blockBuilder: BlockBuilder): BlockWitness {
        val witnessBuilder = blockBuilder.getBlockWitnessBuilder() as MultiSigBlockWitnessBuilder
        assertNotNull(witnessBuilder)
        val blockData = blockBuilder.getBlockData()
        // Simulate other peers sign the block
        val blockHeader = blockData.header
        var i = 0
        while (!witnessBuilder.isComplete()) {
            val sigMaker = cryptoSystem.buildSigMaker(KeyPair(pubKey(i), privKey(i)))
            witnessBuilder.applySignature(sigMaker.signDigest(blockHeader.blockRID))
            i++
        }
        val witness = witnessBuilder.getWitness()
        blockBuilder.commit(witness)
        return witness
    }

}