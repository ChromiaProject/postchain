package net.postchain.devtools.mminfra

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadWriteConnection
import net.postchain.concurrent.util.get
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.ManagedNodeConfigurationProvider
import net.postchain.core.BlockEContext
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManagerExtension
import net.postchain.devtools.awaitDebug
import net.postchain.devtools.utils.ChainUtil
import net.postchain.managed.LocalBlockchainInfo
import net.postchain.managed.ManagedBlockchainConfigurationProvider
import net.postchain.managed.ManagedBlockchainProcessManager
import net.postchain.managed.ManagedNodeDataSource
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

open class TestManagedBlockchainProcessManager(
        postchainContext: PostchainContext,
        blockchainInfrastructure: BlockchainInfrastructure,
        blockchainConfigProvider: BlockchainConfigurationProvider,
        val testDataSource: ManagedNodeDataSource,
        bpmExtensions: List<BlockchainProcessManagerExtension> = listOf()
) : ManagedBlockchainProcessManager(
        postchainContext,
        blockchainInfrastructure,
        blockchainConfigProvider,
        bpmExtensions
) {

    companion object : KLogging()

    private val blockchainStarts = ConcurrentHashMap<Long, BlockingQueue<Long>>()

    fun getCurRemovedBcHeight() = currentRemovedBlockchainHeight

    override fun initManagedEnvironment(dataSource: ManagedNodeDataSource) {
        this.dataSource = dataSource
        (postchainContext.nodeConfigProvider as? ManagedNodeConfigurationProvider)?.setPeerInfoDataSource(dataSource)
        (blockchainConfigProvider as? ManagedBlockchainConfigurationProvider)?.setManagedDataSource(dataSource)
    }

    /**
     * Overriding the original method, so that we now, instead of checking the DB for what
     * BCs to launch we instead
     */
    override fun retrieveBlockchainsToLaunch(): Set<LocalBlockchainInfo> {
        val result = mutableListOf<LocalBlockchainInfo>()
        testDataSource.computeBlockchainInfoList().forEach {
            val brid = it.rid
            val chainIid = ChainUtil.iidOf(brid)
            result.add(LocalBlockchainInfo(chainIid, it.system, it.state))
            retrieveDebug("NOTE TEST! -- launch chainIid: $chainIid,  BC RID: ${brid.toShortHex()} ")
            withReadWriteConnection(blockBuilderStorage, chainIid) { newCtx ->
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
            LinkedBlockingQueue()
        }
    }

    // Marks the BC height directly after the last BC restart.
    // (The ACTUAL BC height will often proceed beyond this height, but we don't track that here)
    var lastHeightStarted = ConcurrentHashMap<Long, Long>()
    var lastConfigStarted = ConcurrentHashMap<Long, ByteArray>()

    /**
     * Adding extra logic for measuring restarts.
     *
     * (This method will run for for every new height where we have a new BC configuration,
     * b/c the BC will get restarted before the configuration can be used.
     * Every time this method runs the [lastHeightStarted] gets updated with the restart height.)
     */
    override fun afterStartBlockchain(chainId: Long) {
        val process = blockchainProcesses[chainId]!!
        val queries = process.blockchainEngine.getBlockQueries()
        val height = queries.getLastBlockHeight().get()
        lastHeightStarted[chainId] = height
        lastConfigStarted[chainId] = process.blockchainEngine.getConfiguration().configHash
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
    fun awaitStarted(nodeIndex: Int, chainId: Long, atLeastHeight: Long, expectedConfigHash: ByteArray? = null) {
        awaitDebug("++++++ AWAIT node idx: " + nodeIndex + ", chain: " + chainId + ", height: " + atLeastHeight)
        while ((lastHeightStarted[chainId] ?: -2L) < atLeastHeight
                || (expectedConfigHash != null && !expectedConfigHash.contentEquals(lastConfigStarted[chainId]))) {
            Thread.sleep(10)
        }
        awaitDebug("++++++ WAIT OVER! node idx: " + nodeIndex + ", chain: " + chainId + ", height: " + atLeastHeight)
    }

    override fun getDatasourceForCurrentBlock(configuration: BlockchainConfiguration, bctx: BlockEContext): ManagedNodeDataSource {
        return testDataSource
    }
}