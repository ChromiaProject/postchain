// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.integrationtest

import assertk.assertThat
import assertk.assertions.*
import net.postchain.api.rest.model.TxRID
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.core.Transaction
import net.postchain.core.TxDetail
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.PostchainTestNode.Companion.DEFAULT_CHAIN_IID
import net.postchain.devtools.assertChainStarted
import net.postchain.devtools.enqueueTxsAndAwaitBuiltBlock
import net.postchain.devtools.testinfra.TestTransaction
import net.postchain.devtools.utils.configuration.system.SystemSetupFactory
import org.awaitility.Awaitility.await
import org.awaitility.Duration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetLastBlocksExplorerTest : IntegrationTestSetup() {

    @BeforeEach
    fun setup() {
        val blockchainConfig = "/net/postchain/devtools/blockexplorer/blockchain_config.xml"
        val sysSetup = SystemSetupFactory.buildSystemSetup(mapOf(1 to blockchainConfig))
        sysSetup.needRestApi = true // We need the API to be running for this test.

        // Creating all nodes
        createNodesFromSystemSetup(sysSetup)

        // Asserting chain 1 is started for all nodes
        await().atMost(Duration.TEN_SECONDS)
                .untilAsserted {
                    nodes.forEach { it.assertChainStarted() }
                }

        nodes[0].enqueueTxsAndAwaitBuiltBlock(DEFAULT_CHAIN_IID, 0, tx(0), tx(1))
        nodes[0].enqueueTxsAndAwaitBuiltBlock(DEFAULT_CHAIN_IID, 1, tx(10), tx(11), tx(12))
        nodes[0].enqueueTxsAndAwaitBuiltBlock(DEFAULT_CHAIN_IID, 2, tx(100), tx(101), tx(102))
    }

    @Test
    fun test_get_all_transactions_info() {
        val transactions = nodes[0].getRestApiModel().getTransactionsInfo(Long.MAX_VALUE, 300)
        assertThat(transactions).hasSize(8)
    }

    @Test
    fun test_get_last_2_transactions_info() {
        val transactions = nodes[0].getRestApiModel().getTransactionsInfo(Long.MAX_VALUE, 2)
        assertThat(transactions).hasSize(2)
        assertThat(transactions[0].blockHeight == 2L)
        assertThat(transactions[1].blockHeight == 2L)
    }

    @Test
    fun test_get_the_first_2_transactions() {
        val last6Txs = nodes[0].getRestApiModel().getTransactionsInfo(Long.MAX_VALUE, 6) // get one tx at block_height = 1
        assertThat(last6Txs).hasSize(6)
        val block1 = last6Txs[5]
        assertThat(block1.blockHeight).isEqualTo(1L) // get block n. 1

        // get 2 txs from blocks before block1 => block0
        val first2Transactions = nodes[0].getRestApiModel().getTransactionsInfo(block1.timestamp, 2)
        assertThat(first2Transactions).hasSize(2)
        assertThat(first2Transactions.map { tx -> tx.blockHeight }.all { it == 0L }).isTrue()
    }

    @Test
    fun test_get_all_blocks() {
        // Asserting blocks and txs
        val blocks = nodes[0].getRestApiModel().getBlocks(Long.MAX_VALUE, 25, false)
        assertThat(blocks).hasSize(3)

        // Block #2
        assertThat(blocks[0].height).isEqualTo(2L)
        assertThat(blocks[0].transactions).hasSize(3)
        assertThat(compareTx(blocks[0].transactions[0], tx(100))).isTrue()
        assertThat(compareTx(blocks[0].transactions[1], tx(101))).isTrue()
        assertThat(compareTx(blocks[0].transactions[2], tx(102))).isTrue()

        // Block #1
        assertThat(blocks[1].height).isEqualTo(1L)
        assertThat(blocks[1].transactions).hasSize(3)
        assertThat(compareTx(blocks[1].transactions[0], tx(10))).isTrue()
        assertThat(compareTx(blocks[1].transactions[1], tx(11))).isTrue()
        assertThat(compareTx(blocks[1].transactions[2], tx(12))).isTrue()

        // Block #0
        assertThat(blocks[2].height).isEqualTo(0L)
        assertThat(blocks[2].transactions).hasSize(2)
        assertThat(compareTx(blocks[2].transactions[0], tx(0))).isTrue()
        assertThat(compareTx(blocks[2].transactions[1], tx(1))).isTrue()
    }

    @Test
    fun test_get_last_2_blocks() {
        val blocks = nodes[0].getRestApiModel().getBlocks(Long.MAX_VALUE, 2, true)
        assertThat(blocks).hasSize(2)

        assertThat(blocks[0].height).isEqualTo(2L)
        assertThat(blocks[1].height).isEqualTo(1L)
    }

    @Test
    fun test_get_one_block() {
        // get a random block and save blockRID
        val randomBlock = nodes[0].getRestApiModel().getBlocks(Long.MAX_VALUE, 1, true)[0]

        val block = nodes[0].getRestApiModel().getBlock(randomBlock.rid, true)
        assertThat(block).isNotNull()
        assertThat(block!!.height).isEqualTo(block.height)
    }

    @Test
    fun test_get_a_block_does_not_exist() {
        val randomBlockRID = "ce4ae9fbb66228a5dbaf89384217d1466df478753f3f3970af9cae8f485100f2".hexStringToByteArray()
        val block = nodes[0].getRestApiModel().getBlock(randomBlockRID, true)
        assertThat(block).isNull()
    }

    @Test
    fun test_get_one_tx() {
        val txRID = tx(0).getRID()

        val tx = nodes[0].getRestApiModel().getTransactionInfo(TxRID((txRID)))
        assertThat(tx).isNotNull()
    }

    fun test_get_a_tx_does_not_exist() {
        val randomTxRID = "ce4ae9fbb66228a5dbaf89384217d1466df478753f3f3970af9cae8f485100f2".hexStringToByteArray()
        val tx = nodes[0].getRestApiModel().getTransactionInfo(TxRID((randomTxRID)))
        assertThat(tx).isNull()
    }

    private fun tx(id: Int): TestTransaction = TestTransaction(id)

    private fun compareTx(actualTx: TxDetail, expectedTx: Transaction): Boolean {
        return (actualTx.data?.toHex() == expectedTx.getRawData().toHex())
                .also {
                    if (!it) {
                        logger.error {
                            "Transactions are not equal:\n" +
                                    "\t actual:\t${actualTx.data?.toHex()}\n" +
                                    "\t expected:\t${expectedTx.getRawData().toHex()}"
                        }
                    }
                }
    }
}
