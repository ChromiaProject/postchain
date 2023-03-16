// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.specialtx

import net.postchain.common.BlockchainRid
import net.postchain.concurrent.util.get
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.specialtx.SpecialTxTestGTXModule
import net.postchain.devtools.testinfra.TestOneOpGtxTransaction
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.GTXTransaction
import net.postchain.gtx.GTXTransactionFactory
import net.postchain.gtx.special.GTXAutoSpecialTxExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

/**
 * Produces blocks containing Special transactions using the simplest possible setup, but as a minimum we need a new
 * custom test module to give us the "__xxx" operations needed.
 */
class BlockWithSpecialTransactionTest : IntegrationTestSetup() {

    private lateinit var gtxTxFactory: GTXTransactionFactory

    val chainId = 1L // We only have one.

    private fun tx(id: Int): GTXTransaction {
        return TestOneOpGtxTransaction(gtxTxFactory, id, "gtx_empty", arrayOf()).getGTXTransaction()
    }

    private fun txBegin(id: Int): GTXTransaction {
        return TestOneOpGtxTransaction(
                gtxTxFactory,
                id,
                GTXAutoSpecialTxExtension.OP_BEGIN_BLOCK,
                arrayOf()
        ).getGTXTransaction()
    }

    private fun txEnd(id: Int): GTXTransaction {
        return TestOneOpGtxTransaction(
                gtxTxFactory,
                id,
                GTXAutoSpecialTxExtension.OP_END_BLOCK,
                arrayOf()
        ).getGTXTransaction()
    }

    @Test
    @Timeout(2, unit = TimeUnit.MINUTES)
    fun testBlockContent() {
        val count = 3
        configOverrides.setProperty("testpeerinfos", createPeerInfos(count))
        configOverrides.setProperty("api.port", 0)
        createNodes(count, "/net/postchain/devtools/specialtx/blockchain_config_3node_gtx.xml")

        // --------------------
        // Needed to create TXs
        // --------------------
        val blockchainRID: BlockchainRid = nodes[0].getBlockchainInstance().blockchainEngine.getConfiguration().blockchainRid
        val module = SpecialTxTestGTXModule() // Had to build a special module for this test
        val cs = Secp256K1CryptoSystem()
        gtxTxFactory = GTXTransactionFactory(blockchainRID, module, cs)

        // --------------------
        // Create TXs
        // --------------------
        var currentHeight = 0L
        buildBlockNoWait(
                nodes, chainId, currentHeight,
                txBegin(0), // We MUST start with a special begin TX (or we will get an exception)
                tx(1),
                txEnd(2) // We MUST end with a special TX
        )
        awaitHeight(chainId, currentHeight)

        currentHeight++
        buildBlockNoWait(
                nodes, chainId, currentHeight,
                txBegin(3),
                tx(4),
                txEnd(5)
        )
        awaitHeight(chainId, currentHeight)

        currentHeight++
        buildBlockNoWait(
                nodes, chainId, currentHeight,
                txBegin(6),
                tx(7),
                txEnd(8)
        )
        awaitHeight(chainId, currentHeight)

        // --------------------
        // Actual test
        // --------------------
        val expectedNumberOfTxs = 3

        val bockQueries = nodes[0].getBlockchainInstance().blockchainEngine.getBlockQueries()
        for (i in 0..2) {

            val blockData = bockQueries.getBlockAtHeight(i.toLong()).get()!!
            //println("block $i fetched.")

            assertEquals(expectedNumberOfTxs, blockData.transactions.size)

            checkForBegin(blockData.transactions[0])
            checkForTx(blockData.transactions[1])
            checkForEnd(blockData.transactions[2])
        }
    }

    private fun checkForBegin(tx: ByteArray) {
        checkForOperation(tx, GTXAutoSpecialTxExtension.OP_BEGIN_BLOCK)
    }

    private fun checkForTx(tx: ByteArray) {
        checkForOperation(tx, "gtx_empty")
    }

    private fun checkForEnd(tx: ByteArray) {
        checkForOperation(tx, GTXAutoSpecialTxExtension.OP_END_BLOCK)
    }

    private fun checkForOperation(tx: ByteArray, opName: String) {
        val txGtx = gtxTxFactory.decodeTransaction(tx) as GTXTransaction
        assertEquals(1, txGtx.ops.size)
        val op = txGtx.ops[0] as GTXOperation
        //println("Op : ${op.toString()}")
        assertEquals(opName, op.data.opName)
    }
}