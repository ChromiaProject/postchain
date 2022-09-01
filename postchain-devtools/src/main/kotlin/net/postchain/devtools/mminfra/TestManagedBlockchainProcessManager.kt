package net.postchain.devtools.mminfra

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadWriteConnection
import net.postchain.common.BlockchainRid
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.block.BlockTrace
import net.postchain.devtools.awaitDebug
import net.postchain.devtools.utils.ChainUtil
import net.postchain.managed.ManagedBlockchainProcessManager
import net.postchain.managed.ManagedNodeDataSource
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

class TestManagedBlockchainProcessManager(
        postchainContext: PostchainContext,
        blockchainInfrastructure: BlockchainInfrastructure,
        blockchainConfigProvider: BlockchainConfigurationProvider,
        val testDataSource: ManagedNodeDataSource)
    : ManagedBlockchainProcessManager(
        postchainContext,
        blockchainInfrastructure,
        blockchainConfigProvider
) {

    companion object : KLogging()

    private val blockchainStarts = ConcurrentHashMap<Long, BlockingQueue<Long>>()

    override fun buildChain0ManagedDataSource(): ManagedNodeDataSource {
        return testDataSource
    }

    /**
     * Overriding the original method, so that we now, instead of checking the DB for what
     * BCs to launch we instead
     */
    override fun retrieveBlockchainsToLaunch(): Set<Long> {
        val result = mutableListOf<Long>()
        testDataSource.computeBlockchainList().forEach {
            val brid = BlockchainRid(it)
            val chainIid = ChainUtil.iidOf(brid)
            result.add(chainIid)
            retrieveDebug("NOTE TEST! -- launch chainIid: $chainIid,  BC RID: ${brid.toShortHex()} ")
            withReadWriteConnection(storage, chainIid) { newCtx ->
                val db = DatabaseAccess.of(newCtx)
                if (db.getChainId(newCtx, brid) == null) {
                    db.initializeBlockchain(newCtx, brid)
                }
            }
        }
        retrieveDebug("NOTE TEST! - End, restart: ${result.size} ")
        return result.toSet()
    }

    private fun getQueue(chainId: Long): BlockingQueue<Long> {
        return blockchainStarts.computeIfAbsent(chainId) {
            LinkedBlockingQueue<Long>()
        }
    }

    // Marks the BC height directly after the last BC restart.
    // (The ACTUAL BC height will often proceed beyond this height, but we don't track that here)
    var lastHeightStarted = ConcurrentHashMap<Long, Long>()

    /**
     * Overriding the original startBlockchain() and adding extra logic for measuring restarts.
     *
     * (This method will run for for every new height where we have a new BC configuration,
     * b/c the BC will get restarted before the configuration can be used.
     * Every time this method runs the [lastHeightStarted] gets updated with the restart height.)
     */
    override fun startBlockchain(chainId: Long, bTrace: BlockTrace?): BlockchainRid {
        val blockchainRid = super.startBlockchain(chainId, bTrace)
        val process = blockchainProcesses[chainId]!!
        val queries = process.blockchainEngine.getBlockQueries()
        val height = queries.getBestHeight().get()
        lastHeightStarted[chainId] = height
        return blockchainRid
    }

    /**
     * Awaits a start/restart of a BC.
     *
     * @param nodeIndex the node we should wait for
     * @param chainId the chain we should wait for
     * @param atLeastHeight the height we should wait for. Note that this height MUST be a height where we have a
     *           new BC configuration kicking in, because that's when the BC will be restarted.
     *           Example: if a new BC config starts at height 10, then we should put [atLeastHeight] to 9.
     */
    fun awaitStarted(nodeIndex: Int, chainId: Long, atLeastHeight: Long) {
        awaitDebug("++++++ AWAIT node idx: " + nodeIndex + ", chain: " + chainId + ", height: " + atLeastHeight)
        while (lastHeightStarted.get(chainId) ?: -2L < atLeastHeight) {
            Thread.sleep(10)
        }
        awaitDebug("++++++ WAIT OVER! node idx: " + nodeIndex + ", chain: " + chainId + ", height: " + atLeastHeight)
    }
}