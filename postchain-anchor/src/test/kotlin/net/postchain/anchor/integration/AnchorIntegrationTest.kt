package net.postchain.anchor.integration

import mu.KLogging
import net.postchain.anchor.AnchorGTXModule
import net.postchain.anchor.AnchorSpecialTxExtension
import net.postchain.anchor.AnchorTestGTXModule
import net.postchain.base.SECP256K1CryptoSystem
import net.postchain.base.withWriteConnection
import net.postchain.configurations.GTXTestModule
import net.postchain.core.BlockchainRid
import net.postchain.devtools.TxCache
import net.postchain.devtools.utils.GtxTxIntegrationTestSetup
import net.postchain.devtools.utils.configuration.SystemSetup
import net.postchain.gtx.*
import org.junit.Assert
import org.junit.Test
import java.lang.Exception

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

    private lateinit var anchorGtxTxFactory: GTXTransactionFactory // Need it's own since BC RID is part of the factory

    private val ANCHOR_CHAIN_ID = 2 // Only for this test, we don't have a hard ID for anchoring.


    /**
     * Simple happy test to see that we can run 3 nodes with:
     * - a normal chain and
     * - an anchor chain.
     */
    @Test
    fun happyAnchor() {
        val mapBcFiles: Map<Int, String> = mapOf(
            1 to "/net/postchain/anchor/integration/blockchain_config_1.xml",
            ANCHOR_CHAIN_ID to "/net/postchain/anchor/integration/blockchain_config_2_anchor.xml"
        )

        val sysSetup = SystemSetup.buildComplexSetup(mapBcFiles)

        // -----------------------------
        // Important!! We don't want to create any regular test transactions for the Anchor chain
        // -----------------------------
        sysSetup.blockchainMap[ANCHOR_CHAIN_ID]!!.setShouldHaveNormalTx(false)

        runXNodes(sysSetup) // Starts all chains on all nodes

        // --------------------
        // ChainId = 1: Create all blocks
        // --------------------
        val txCache = TxCache(mutableMapOf())

        // Only for chain 1 see "shouldHaveNormalTx" setting
        runXNodesWithYTxPerBlock(4, 5, sysSetup, txCache) // This is waiting for blocks to finish too
        // Only for chain 1 see "shouldHaveNormalTx" setting
        runXNodesAssertions(4, 5, sysSetup, txCache)

        // --------------------
        // Need a TX factory for testing
        // --------------------
        val cs = SECP256K1CryptoSystem()

        // Anchor (special) TX
        val anchorBlockchainRID: BlockchainRid = sysSetup.blockchainMap[ANCHOR_CHAIN_ID]!!.rid
        val anchorModule = buildCompositeGTXModule()

        anchorGtxTxFactory = GTXTransactionFactory(anchorBlockchainRID, anchorModule, cs)

        // --------------------
        // ChainId = 2: Check that we begin with nothing
        // --------------------
        val bockQueries = nodes[0].getBlockchainInstance(ANCHOR_CHAIN_ID.toLong()).getEngine().getBlockQueries()

        // --------------------
        // ChainId = 2: Build first anchor block
        // --------------------

        val heightZero = 0
        buildBlocks(0, ANCHOR_CHAIN_ID.toLong(), heightZero)

        // --------------------
        // ChainId = 2: Actual test
        // --------------------
        val expectedNumberOfTxs = 1  // Only the begin TX

        val blockDataFull = bockQueries.getBlockAtHeight(heightZero.toLong()).get()!!
        System.out.println("block $heightZero fetched.")
        Assert.assertEquals(expectedNumberOfTxs, blockDataFull.transactions.size)
        val theOnlyTx = blockDataFull.transactions[0] // There is only one

        checkForAnchorOp(
            theOnlyTx,
            4,
            anchorGtxTxFactory // We must use correct factory or else we cannot decode the transaction due to incorrect BC RID.
        )
    }

    /**
     * There MUST be a prettier way to do this (without triggering the configurations and creating tables etc)
     */
    private fun buildCompositeGTXModule(): CompositeGTXModule {
        val moduleList = listOf(AnchorGTXModule(), AnchorTestGTXModule(), StandardOpsGTXModule())
        val anchorModule = CompositeGTXModule(moduleList.toTypedArray(), false)
        val _opmap = mutableMapOf<String, GTXModule>()
        for (m in moduleList) {
            for (op in m.getOperations()) {
                _opmap[op] = m
            }
        }
        anchorModule.opmap = _opmap.toMap()
        return anchorModule
    }

    private fun checkForOperation(tx: ByteArray, opName: String, numberOfOperations: Int, currTxFactory: GTXTransactionFactory) {
        try {
            val txGtx = currTxFactory.decodeTransaction(tx) as GTXTransaction // For Anchor chain this MUST be the anchor GTX Tx Factory
            Assert.assertEquals(numberOfOperations, txGtx.ops.size)
            val op = txGtx.ops[0] as GTXOperation
            System.out.println("Op : ${op.toString()}")
            Assert.assertEquals(opName, op.data.opName)
        } catch (e: Exception) {
            Assert.fail("Could not decode due to: ${e.message}")
        }
    }

    private fun checkForAnchorOp(tx: ByteArray, numberOfOperations: Int, currTxFactory: GTXTransactionFactory) {
        checkForOperation(tx, AnchorSpecialTxExtension.OP_BLOCK_HEADER, numberOfOperations, currTxFactory)
    }

}
