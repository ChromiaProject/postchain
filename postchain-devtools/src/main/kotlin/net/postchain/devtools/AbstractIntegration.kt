package net.postchain.devtools

import net.postchain.common.exception.ProgrammerMistake
import net.postchain.concurrent.util.get
import net.postchain.core.BlockchainEngine
import net.postchain.core.block.BlockBuilder
import net.postchain.core.block.BlockTrace
import net.postchain.core.block.BlockWitness
import net.postchain.core.block.MultiSigBlockWitnessBuilder
import net.postchain.crypto.KeyPair
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.gtv.Gtv
import net.postchain.gtv.gtvml.GtvMLParser
import org.junit.jupiter.api.Assertions
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService


/**
 * Postchain has different test categories:
 *
 * 1. Unit tests - A test without dependencies.
 * 2. Integration tests - Depends on the DB.
 * 3. Nightly test - Tests with a lot of data.
 * 4. Manual test - Requires some manual work to run.
 *
 * Type 2-4 are often heavy, and should inherit this class to get help doing common tasks.
 * Examples of tasks this class will help you with are:
 *
 * - Creating a configuration for:
 *    - single node
 *    - multiple nodes
 * - Verifying all transactions in the BC
 * - Building and committing a block
 * - etc
 */
abstract class AbstractIntegration {

    val cryptoSystem = Secp256K1CryptoSystem()
    protected val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    protected var expectedSuccessRids = mutableMapOf<Long, MutableList<ByteArray>>()

    /**
     * Put logic in here that should run after each test (the "@after" annotation will guarantee execution)
     */
    abstract fun tearDown()

    protected fun readBlockchainConfig(blockchainConfigFilename: String): Gtv {
        return GtvMLParser.parseGtvML(
                javaClass.getResource(blockchainConfigFilename).readText())
    }

    protected fun buildBlockAndCommit(engine: BlockchainEngine) {
        val (blockBuilder, _) = engine.buildBlock()

        try {
            commitBlock(blockBuilder)
        } catch (_: ProgrammerMistake) {
            // Block is already committed (presumably by BaseBlockBuildingStrategy)
        }
    }

    protected fun buildBlockAndCommit(node: PostchainTestNode) {
        buildBlockAndCommit(node.getBlockchainInstance().blockchainEngine)
    }

    private fun commitBlock(blockBuilder: BlockBuilder): BlockWitness {
        val witnessBuilder = blockBuilder.getBlockWitnessBuilder() as MultiSigBlockWitnessBuilder
        val blockData = blockBuilder.getBlockData()
        // Simulate other peers sign the block
        val blockHeader = blockData.header
        var i = 0
        while (!witnessBuilder.isComplete()) {
            val sigMaker = cryptoSystem.buildSigMaker(KeyPair(KeyPairHelper.pubKey(i), KeyPairHelper.privKey(i)))
            witnessBuilder.applySignature(sigMaker.signDigest(blockHeader.blockRID))
            i++
        }
        val witness = witnessBuilder.getWitness()
        blockBuilder.setBTrace(BlockTrace.build(blockHeader.blockRID, null))
        blockBuilder.commit(witness)
        return witness
    }

    protected fun getTxRidsAtHeight(node: PostchainTestNode, height: Long): Array<ByteArray> {
        val blockQueries = node.getBlockchainInstance().blockchainEngine.getBlockQueries()
        val blockRid = blockQueries.getBlockRid(height).get()
        return blockQueries.getBlockTransactionRids(blockRid!!).get().toTypedArray()
    }

    protected fun getLastHeight(node: PostchainTestNode): Long {
        return node.getBlockchainInstance().blockchainEngine.getBlockQueries().getLastBlockHeight().get()
    }

    protected fun verifyBlockchainTransactions(node: PostchainTestNode) {
        val expectAtLeastHeight = expectedSuccessRids.keys.reduce { acc, l -> maxOf(l, acc) }
        val lastHeight = getLastHeight(node)
        Assertions.assertTrue(lastHeight >= expectAtLeastHeight)
        for (height in 0..lastHeight) {
            val txRidsAtHeight = getTxRidsAtHeight(node, height)

            val expectedRidsAtHeight = expectedSuccessRids[height]
            if (expectedRidsAtHeight == null) {
                Assertions.assertArrayEquals(arrayOf(), txRidsAtHeight)
            } else {
                Assertions.assertArrayEquals(expectedRidsAtHeight.toTypedArray(), txRidsAtHeight)
            }
        }
    }
}
