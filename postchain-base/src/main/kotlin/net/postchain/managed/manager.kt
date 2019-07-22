package net.postchain.managed

import net.postchain.StorageBuilder
import net.postchain.base.*
import net.postchain.base.data.DatabaseAccess
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.blockchain.ManualBlockchainConfigurationProvider
import net.postchain.config.node.ManagedNodeConfigurationProvider
import net.postchain.config.node.NodeConfig
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.config.node.PeerInfoDataSource
import net.postchain.core.*
import net.postchain.ebft.BaseEBFTInfrastructureFactory
import net.postchain.gtv.GtvFactory.gtv
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.ScalarHandler
import java.util.concurrent.TimeUnit

class RealManagedBlockchainConfigurationProvider(val nodeConfigProvider: NodeConfigurationProvider)
    : BlockchainConfigurationProvider {

    private lateinit var dataSource: ManagedNodeDataSource
    val systemProvider = ManualBlockchainConfigurationProvider(nodeConfigProvider)

    fun setDataSource(dataSource: ManagedNodeDataSource) {
        this.dataSource = dataSource
    }

    private fun getConfigurationFromDataSource(eContext: EContext): ByteArray? {
        val dba = DatabaseAccess.of(eContext)
        /* val newCtx = BaseEContext(eContext.conn,
                eContext.chainID, eContext.nodeID, dba)*/
        val blockchainRID = dba.getBlockchainRID(eContext)
        val height = dba.getLastBlockHeight(eContext) + 1
        return dataSource.getConfiguration(blockchainRID!!, height)
    }

    override fun getConfiguration(eContext: EContext, chainId: Long): ByteArray? {
        if (chainId == 0L) {
            return systemProvider.getConfiguration(eContext, chainId)
        } else {
            if (::dataSource.isInitialized) {
                if (eContext.chainID != chainId) throw IllegalStateException("chainID mismatch")
                return getConfigurationFromDataSource(eContext)
            } else {
                throw IllegalStateException("Using managed blockchain configuration provider before it's properly initialized")
            }
        }
    }

    override fun needsConfigurationChange(eContext: EContext, chainId: Long): Boolean {
        fun checkNeedConfChangeViaDataSource(): Boolean {
            val dba = DatabaseAccess.of(eContext)
            val blockchainRID = dba.getBlockchainRID(eContext)
            val height = dba.getLastBlockHeight(eContext)
            val nch = dataSource.findNextConfigurationHeight(blockchainRID!!, height)
            return (nch != null) && (nch == height + 1)
        }

        if (chainId == 0L) {
            return systemProvider.needsConfigurationChange(eContext, chainId)
        } else {
            if (::dataSource.isInitialized) {
                return checkNeedConfChangeViaDataSource()
            } else {
                throw IllegalStateException("Using managed blockchain configuration provider before it's properly initialized")
            }
        }
    }

}

interface ManagedNodeDataSource : PeerInfoDataSource {
    fun getPeerListVersion(): Long
    fun computeBlockchainList(ctx: EContext): List<ByteArray>
    fun getConfiguration(blockchainRID: ByteArray, height: Long): ByteArray?
    fun findNextConfigurationHeight(blockchainRID: ByteArray, height: Long): Long?
}

class GTXManagedNodeDataSource(val q: BlockQueries, val nc: NodeConfig) : ManagedNodeDataSource {
    override fun getPeerInfos(): Array<PeerInfo> {
        val res = q.query("nm_get_peer_infos", gtv(mapOf()))
        val a = res.get().asArray()
        return a.map {
            val pia = it.asArray()
            PeerInfo(
                    pia[0].asString(),
                    pia[1].asInteger().toInt(),
                    pia[2].asByteArray()
            )
        }.toTypedArray()
    }

    override fun getPeerListVersion(): Long {
        val res = q.query("nm_get_peer_list_version", gtv(mapOf()))
        return res.get().asInteger()
    }

    override fun computeBlockchainList(ctx: EContext): List<ByteArray> {
        val res = q.query("nm_compute_blockchain_list",
                gtv("node_id" to gtv(nc.pubKeyByteArray))
        )
        val a = res.get().asArray()
        return a.map { it.asByteArray() }
    }

    override fun findNextConfigurationHeight(blockchainRID: ByteArray, height: Long): Long? {
        val res = q.query("nm_find_next_configuration_height",
                gtv("blockchain_rid" to gtv(blockchainRID),
                        "height" to gtv(height))).get()
        return if (res.isNull()) null else res.asInteger()
    }

    override fun getConfiguration(blockchainRID: ByteArray, height: Long): ByteArray? {
        val res = q.query("nm_get_blockchain_configuration",
                gtv("blockchain_rid" to gtv(blockchainRID),
                        "height" to gtv(height))).get()
        if (res.isNull())
            return null
        else
            return res.asByteArray()
    }
}

class ManagedInfrastructureFactory : BaseEBFTInfrastructureFactory() {
    override fun makeProcessManager(
            nodeConfigProvider: NodeConfigurationProvider,
            blockchainInfrastructure: BlockchainInfrastructure
    ): BlockchainProcessManager {
        return ManagedBlockchainProcessManager(
                blockchainInfrastructure,
                nodeConfigProvider
        )
    }
}

class Manager(val procMan: BlockchainProcessManager,
              val nodeConfigProvider: NodeConfigurationProvider) {

    private val queryRunner = QueryRunner()
    private val longRes = ScalarHandler<Long>()
    lateinit var dataSource: ManagedNodeDataSource
    var lastPeerListVersion: Long? = null

    fun useChain0BlockQueries(bq: BlockQueries): ManagedNodeDataSource {
        dataSource = GTXManagedNodeDataSource(bq, nodeConfigProvider.getConfiguration())
        return dataSource
    }

    fun applyBlockchainList(ctx: EContext, list: List<ByteArray>, forceReload: Boolean) {
        val dba = DatabaseAccess.of(ctx)
        for (elt in list) {
            val ci = dba.getChainId(ctx, elt)
            if (ci == null) {
                addBlockchain(ctx, elt)
            } else if (ci != 0L) {
                val proc = procMan.retrieveBlockchain(ci)
                if (proc == null || forceReload)
                    procMan.startBlockchainAsync(ci)
            }
        }
    }

    fun addBlockchain(ctx: EContext, blockchainRID: ByteArray) {
        val dba = DatabaseAccess.of(ctx)
        // find the next unused chain_id starting from 100
        val new_chain_id = maxOf(
                queryRunner.query(ctx.conn, "SELECT MAX(chain_id) FROM blockchains", longRes) + 1,
                100)
        val newCtx = BaseEContext(ctx.conn, new_chain_id, ctx.nodeID, dba)
        dba.checkBlockchainRID(newCtx, blockchainRID)
        procMan.startBlockchainAsync(new_chain_id)
    }

    fun maybeUpdateChain0(ctx: EContext, reload: Boolean) {
        val dba = DatabaseAccess.of(ctx)
        val brid = dba.getBlockchainRID(ctx)!!
        val height = dba.getLastBlockHeight(ctx)
        val nextConfHeight = dataSource.findNextConfigurationHeight(brid, height)
        if (nextConfHeight != null) {
            if (BaseConfigurationDataStore.findConfiguration(ctx, nextConfHeight) != nextConfHeight) {
                BaseConfigurationDataStore.addConfigurationData(
                        ctx, nextConfHeight,
                        dataSource.getConfiguration(brid, nextConfHeight)!!
                )
            }
        }
        if (reload)
            procMan.startBlockchainAsync(0)
    }

    fun runPeriodic(ctx: EContext) {
        val peerListVersion = dataSource.getPeerListVersion()
        val reloadBlockchains = (lastPeerListVersion != null) && (lastPeerListVersion != peerListVersion)
        lastPeerListVersion = peerListVersion

        maybeUpdateChain0(ctx, reloadBlockchains)
        applyBlockchainList(ctx, dataSource.computeBlockchainList(ctx), reloadBlockchains)
    }
}

class ManagedBlockchainProcessManager(
        blockchainInfrastructure: BlockchainInfrastructure,
        nodeConfigProvider: NodeConfigurationProvider
) : BaseBlockchainProcessManager(
        blockchainInfrastructure,
        nodeConfigProvider,
        RealManagedBlockchainConfigurationProvider(nodeConfigProvider)
) {
    val manager = Manager(this, nodeConfigProvider)
    var updaterThreadStarted = false

    fun startUpdaterThread() {
        if (updaterThreadStarted) return
        updaterThreadStarted = true
        executor.scheduleWithFixedDelay(
                {
                    try {
                        withWriteConnection(storage, 0) {
                            manager.runPeriodic(it)
                            true
                        }
                    } catch (e: Exception) {
                        logger.error("Unhandled exception in manager.runPeriodic", e)
                    }
                },
                10, 10, TimeUnit.SECONDS
        )
    }

    fun makeChain0BlockQueries(): BlockQueries {
        val chainId = 0L
        var bq: BlockQueries? = null

        withWriteConnection(storage, chainId) { eContext ->
            val configuration = blockchainConfigProvider.getConfiguration(eContext, chainId)
            if (configuration == null) throw ProgrammerMistake("chain0 configuration not found")
            val blockchainRID = DatabaseAccess.of(eContext).getBlockchainRID(eContext)!! // TODO: [et]: Fix Kotlin NPE
            val context = BaseBlockchainContext(blockchainRID, NODE_ID_AUTO, chainId, null)
            val blockchainConfig = blockchainInfrastructure.makeBlockchainConfiguration(configuration, context)
            blockchainConfig.initializeDB(eContext)
            val storage = StorageBuilder.buildStorage(nodeConfigProvider.getConfiguration(), NODE_ID_NA)
            bq = blockchainConfig.makeBlockQueries(storage)
            true
        }
        return bq!!
    }

    override fun startBlockchain(chainId: Long) {
        if (chainId == 0L) {
            val chain0BlockQueries = makeChain0BlockQueries()
            val dataSource = manager.useChain0BlockQueries(chain0BlockQueries)
            (blockchainConfigProvider as RealManagedBlockchainConfigurationProvider).setDataSource(dataSource)
            if (nodeConfigProvider is ManagedNodeConfigurationProvider) {
                nodeConfigProvider.setPeerInfoDataSource(dataSource)
            } else {
                logger.warn { "Node config is not managed, no peer info updates possible" }
            }
        }
        super.startBlockchain(chainId)
        if (chainId == 0L) {
            startUpdaterThread()
        }
    }
}