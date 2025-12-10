// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools

import assertk.assertThat
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import net.postchain.common.toHex
import net.postchain.common.tx.EnqueueTransactionResult
import net.postchain.common.wrap
import net.postchain.concurrent.util.get
import net.postchain.core.Transaction
import net.postchain.core.block.BlockQueries
import net.postchain.core.block.MultiSigBlockWitness
import net.postchain.crypto.Signature
import net.postchain.devtools.testinfra.TestTransaction
import net.postchain.gtv.Gtv
import net.postchain.gtx.CompositeGTXModule
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXModuleAware
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeoutException
import kotlin.time.Duration

fun PostchainTestNode.addBlockchainAndStart(chainId: Long, blockchainConfig: Gtv) {
    val bcRid = addBlockchain(chainId, blockchainConfig)
    mapBlockchainRID(chainId, bcRid)
    startBlockchain(chainId)
}

fun PostchainTestNode.assertChainStarted(chainId: Long = PostchainTestNode.DEFAULT_CHAIN_IID) {
    assertThat(retrieveBlockchain(chainId)).isNotNull()
}

fun PostchainTestNode.assertChainNotStarted(chainId: Long = PostchainTestNode.DEFAULT_CHAIN_IID) {
    assertThat(retrieveBlockchain(chainId)).isNull()
}

fun PostchainTestNode.assertNodeConnectedWith(chainId: Long, vararg nodes: PostchainTestNode) {
    assertEquals(nodes.map(PostchainTestNode::pubKey).toSet(), networkTopology(chainId).keys)
}

fun <T> PostchainTestNode.query(chainId: Long, action: (BlockQueries) -> CompletionStage<T>): T? {
    return retrieveBlockchain(chainId)?.blockchainEngine?.getBlockQueries()?.run {
        action(this)
    }?.get()
}

fun PostchainTestNode.blockSignatures(chainId: Long, height: Long): Array<Signature> {
    val block = query(chainId) { it.getBlockAtHeight(height) }
    assertNotNull(block)
    return (block!!.witness as? MultiSigBlockWitness)
            ?.getSignatures() ?: emptyArray()
}

fun PostchainTestNode.currentHeight(chainId: Long): Long {
    return query(chainId) { it.getLastBlockHeight() } ?: -1L
}

fun PostchainTestNode.awaitedHeight(chainId: Long): Long {
    return query(chainId) { it.getLastBlockHeight() }
            ?.plus(1) ?: -1L
}

fun PostchainTestNode.buildBlocksUpTo(chainId: Long, height: Long) {
    strategy(chainId).buildBlocksUpTo(height)
}

private fun PostchainTestNode.strategy(chainId: Long) =
        blockBuildingStrategy(chainId) as OnDemandBlockBuildingStrategy

/**
 * Builds blocks up to the specified height and waits for them to be committed.
 * This method triggers block building and then waits for the blocks to be committed.
 *
 * **Warning:** Don't use during reconfiguration -- holds a reference to the old block building
 * strategy and will wait indefinitely on a stale object.
 *
 * @param chainId the chain ID to build blocks on
 * @param height the target block height to build and wait for
 * @param timeout  time to wait for each block
 *
 * @throws TimeoutException if timeout
 */
fun PostchainTestNode.awaitBuiltBlock(chainId: Long, height: Long, timeout: Duration = Duration.INFINITE) {
    val strategy = strategy(chainId)

    strategy.buildBlocksUpTo(height)
    strategy.awaitCommitted(height.toInt(), timeout)
}

/**
 * Waits for the blockchain to reach the specified height without actively building blocks.
 * This method only waits for blocks to be committed, unlike [awaitBuiltBlock] which also triggers block building.
 *
 * **Warning:** Don't use during reconfiguration -- holds a reference to the old block building
 * strategy and will wait indefinitely on a stale object.
 *
 * @param chainId the chain ID to wait on
 * @param height the target block height to wait for
 * @param timeout  time to wait for each block (default is Duration.INFINITE)
 *
 * @throws TimeoutException if timeout
 */
fun PostchainTestNode.awaitHeight(chainId: Long, height: Long, timeout: Duration = Duration.INFINITE) {
    strategy(chainId).awaitCommitted(height.toInt(), timeout)
}

fun PostchainTestNode.enqueueTxs(chainId: Long, vararg txs: Transaction): Boolean {
    return retrieveBlockchain(chainId)
            ?.let { process ->
                val txQueue = process.blockchainEngine.getTransactionQueue()
                txs.forEach {
                    val result = txQueue.enqueue(it)
                    if (result != EnqueueTransactionResult.OK) {
                        val msg = if (result == EnqueueTransactionResult.INVALID) txQueue.getRejectionReason(it.getRID().wrap())?.first?.message ?: "INVALID" else result.toString()
                        PostchainTestNode.logger.warn("Transaction ${it.getRID().toHex()} could not be enqueued: $msg")
                   }
                }
                true
            }
            ?: false
}

fun PostchainTestNode.enqueueTxsAndAwaitBuiltBlock(chainId: Long, height: Long, vararg txs: TestTransaction) {
    enqueueTxs(chainId, *txs)
    awaitBuiltBlock(chainId, height)
}

fun PostchainTestNode.getModules(chainId: Long = PostchainTestNode.DEFAULT_CHAIN_IID): Array<GTXModule> {
    val configuration = retrieveBlockchain(chainId)
            ?.blockchainEngine
            ?.getConfiguration()

    return when (configuration) {
        is GTXModuleAware -> collectModules(configuration.module)
        else -> emptyArray()
    }
}

private fun collectModules(module: GTXModule): Array<GTXModule> {
    return (module as? CompositeGTXModule)?.modules
            ?: arrayOf(module)
}