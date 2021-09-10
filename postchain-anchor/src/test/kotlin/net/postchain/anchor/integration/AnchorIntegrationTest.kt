package net.postchain.anchor.integration

import mu.KLogging
import net.postchain.anchor.AnchorGTXModule
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.core.BlockchainRid
import net.postchain.devtools.TxCache
import net.postchain.devtools.utils.GtxTxIntegrationTestSetup
import net.postchain.devtools.utils.configuration.system.SystemSetupFactory
import net.postchain.gtx.GTXAutoSpecialTxExtension
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.GTXTransaction
import net.postchain.gtx.GTXTransactionFactory
import org.junit.Assert
import org.junit.Test

/**
 * Main idea is to have one "source" blockchain that generates block, so that these blocks can be anchored by another
 * "anchor" blockchain. If this work the golden path of anchoring should work.
 *
 * It doesn't matter what the "source" blocks contain.
 * Produces blocks containing Special transactions using the simplest possible setup, but as a minimum we need a new
 * custom test module to give us the "__xxx" operations needed.
 */
class AnchorIntegrationTest : GtxTxIntegrationTestSetup() {

    companion object : KLogging()

    private lateinit var gtxTxFactory: GTXTransactionFactory

    private val ANCHOR_CHAIN_ID = 2 // Only for this test, we don't have a hard ID for anchoring.


    /**
     * Simple happy test to see that we can run 3 nodes with:
     * - 1 normal chain and
     * - 1 anchor chain.
     */
    @Test
    fun happyAnchor() {
        val mapBcFiles: Map<Int, String> = mapOf(
            1 to "/net/postchain/anchor/integration/blockchain_config_1.xml",
            ANCHOR_CHAIN_ID to "/net/postchain/anchor/integration/blockchain_config_2_anchor.xml"
        )

        val sysSetup = SystemSetupFactory.buildSystemSetup(mapBcFiles)

        runXNodes(sysSetup)

        // --------------------
        // ChainId = 1: Create all blocks
        // --------------------
        val txCache = TxCache(mutableMapOf())
        runXNodesWithYTxPerBlock(4, 5, sysSetup, txCache) // This is waiting for blocks to finish too
        runXNodesAssertions(4, 5, sysSetup, txCache)

        // --------------------
        // Need a TX factory for testing
        // --------------------
        val blockchainRID: BlockchainRid = nodes[0].getBlockchainInstance().getEngine().getConfiguration().blockchainRid
        val module = AnchorGTXModule()
        val cs = SECP256K1CryptoSystem()
        gtxTxFactory = GTXTransactionFactory(blockchainRID, module, cs)

        // --------------------
        // ChainId = 2: Check that we begin with nothing
        // --------------------
        val bockQueries = nodes[0].getBlockchainInstance(ANCHOR_CHAIN_ID.toLong()).getEngine().getBlockQueries()
        val blockDataEmpty = bockQueries.getBlockAtHeight(0.toLong())
        Assert.assertNull(blockDataEmpty)

        // --------------------
        // ChainId = 2: Build first anchor block
        // --------------------
        buildBlocks(0, ANCHOR_CHAIN_ID.toLong(), 0)

        // --------------------
        // ChainId = 2: Actual test
        // --------------------
        val expectedNumberOfTxs = 1  // Only the begin TX

        val i = 0
        val blockDataFull = bockQueries.getBlockAtHeight(i.toLong()).get()!!
        System.out.println("block $i fetched.")
        Assert.assertEquals(expectedNumberOfTxs, blockDataFull.transactions.size)


        checkForBegin(blockDataFull.transactions[0], 4, gtxTxFactory)
        // checkForTx(blockData.transactions[0])
        //checkForEnd(blockData.transactions[2])
    }

    private fun checkForOperation(tx: ByteArray, opName: String, numberOfOperations: Int, gtxTxFactory: GTXTransactionFactory) {
        val txGtx = gtxTxFactory.decodeTransaction(tx) as GTXTransaction
        Assert.assertEquals(numberOfOperations, txGtx.ops.size)
        val op = txGtx.ops[0] as GTXOperation
        System.out.println("Op : ${op.toString()}")
        Assert.assertEquals(opName, op.data.opName)
    }

    // -------------------------------
    // Used to test the "normal" chain
    // -------------------------------
    //private fun checkForTx(tx: ByteArray) {
    //    checkForOperation(tx, "gtx_empty")
    //}

    // -------------------------------
    // Used to test the Anchor chain
    // -------------------------------
    private fun checkForBegin(tx: ByteArray, numberOfOperations: Int, gtxTxFactory: GTXTransactionFactory) {
        checkForOperation(tx, GTXAutoSpecialTxExtension.OP_BEGIN_BLOCK, numberOfOperations, gtxTxFactory)
    }


    //private fun checkForEnd(tx: ByteArray) {
    //    checkForOperation(tx, GTXAutoSpecialTxExtension.OP_END_BLOCK)
    //}

}
