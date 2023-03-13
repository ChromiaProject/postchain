// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.managed

import mu.KLogging
import net.postchain.base.data.DatabaseAccess
import net.postchain.common.BlockchainRid
import net.postchain.config.blockchain.AbstractBlockchainConfigurationProvider
import net.postchain.config.blockchain.ManualBlockchainConfigurationProvider
import net.postchain.core.EContext
import java.util.concurrent.ConcurrentHashMap

open class ManagedBlockchainConfigurationProvider : AbstractBlockchainConfigurationProvider() {

    protected lateinit var dataSource: ManagedNodeDataSource

    // Used to access Chain0 configs which are preloaded and validated in ManagedBlockchainProcessManager.preloadChain0Configuration().
    private val localProvider = ManualBlockchainConfigurationProvider()
    protected val pendingConfigurations = ConcurrentHashMap<Long, PendingBlockchainConfigurationStatus>()

    companion object : KLogging()

    // Feature toggle
    protected open fun isPcuEnabled(): Boolean = dataSource.nmApiVersion >= 5
            && (dataSource as? BaseManagedNodeDataSource)?.appConfig?.getEnvOrBoolean("POSTCHAIN_PCU", "pcu", false) ?: false

    fun setManagedDataSource(dataSource: ManagedNodeDataSource) {
        this.dataSource = dataSource
    }

    override fun getActiveBlocksConfiguration(eContext: EContext, chainId: Long): ByteArray? {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        return if (chainId == 0L) {
            localProvider.getActiveBlocksConfiguration(eContext, chainId)
        } else {
            logger.debug { "getConfiguration() - Fetching configuration from chain0 (for chain: $chainId)" }
            if (::dataSource.isInitialized) {
                getConfigurationFromDataSource(eContext)
            } else {
                throw IllegalStateException("Using managed blockchain configuration provider before it's properly initialized")
            }
        }
    }

    override fun activeBlockNeedsConfigurationChange(eContext: EContext, chainId: Long): Boolean {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        return if (chainId == 0L) {
            localProvider.activeBlockNeedsConfigurationChange(eContext, chainId)
        } else {
            if (::dataSource.isInitialized) {
                checkNeedConfChangeViaDataSource(eContext)
            } else {
                throw IllegalStateException("Using managed blockchain configuration provider before it's properly initialized")
            }
        }
    }

    open fun isManagedDatasourceReady(eContext: EContext): Boolean {
        return if (isPcuEnabled()) {
            val dba = DatabaseAccess.of(eContext)
            val blockchainRid = getBlockchainRid(eContext, dba)
            val activeHeight = getActiveBlocksHeight(eContext, dba)
            val pendingConfig = pendingConfigurations[eContext.chainID]
            val logPrefix = "isManagedDatasourceReady(${eContext.chainID})"

            if (pendingConfig != null) {
                logger.debug { "$logPrefix: pending config detected for chain ${eContext.chainID}: $pendingConfig" }
                if (pendingConfig.isBlockBuilt) {
                    logger.debug { "$logPrefix: block at height ${pendingConfig.height} is already built with a pending config" }
                    val applied = dataSource.isPendingBlockchainConfigurationApplied(blockchainRid, activeHeight, pendingConfig.config.baseConfigHash.data)
                    if (applied) {
                        pendingConfigurations.remove(eContext.chainID)
                        logger.debug { "$logPrefix: pending config is applied" }
                    } else {
                        logger.debug { "$logPrefix: pending config is not applied yet" }
                    }
                    applied
                } else {
                    logger.debug { "$logPrefix: block at height ${pendingConfig.height} is not yet built with a pending config" }
                    true // block is not yet built with a pending config
                }
            } else {
                true // there is no pending config
            }
        } else {
            true // non-PCU mode
        }
    }

    fun afterCommit(chainId: Long, height: Long) {
        val logPrefix = "afterCommit($chainId, $height)"
        val pendingConfig = pendingConfigurations[chainId]
        if (pendingConfig != null) {
            logger.debug { "$logPrefix: pendingConfig detected: $pendingConfig" }
            if (pendingConfig.height != height) {
                logger.error { "$logPrefix: unexpected height detected: pendingConfig.height: ${pendingConfig.height}, afterCommit.height: $height" }
            } else {
                pendingConfig.isBlockBuilt = true
                logger.debug { "$logPrefix: block is built at height ${pendingConfig.height} with the pending config" }
            }
        } else {
            logger.debug { "$logPrefix: no pendingConfig detected" }
        }
    }


    /**
     * Same principle for "historic" as for "current"
     */
    override fun getHistoricConfigurationHeight(eContext: EContext, chainId: Long, historicBlockHeight: Long): Long? {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        return if (chainId == 0L) {
            localProvider.getHistoricConfigurationHeight(eContext, chainId, historicBlockHeight)
        } else {
            // We ignore local setting, go to Chain0 directly
            val dba = DatabaseAccess.of(eContext)
            val blockchainRid = getBlockchainRid(eContext, dba)
            val lastSavedBlockHeight = dba.getLastBlockHeight(eContext)
            dataSource.findNextConfigurationHeight(blockchainRid.data, lastSavedBlockHeight)
        }
    }

    /**
     * Same principle for "historic" as for "current"
     */
    override fun getHistoricConfiguration(eContext: EContext, chainId: Long, historicBlockHeight: Long): ByteArray? {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        return if (chainId == 0L) {
            localProvider.getHistoricConfiguration(eContext, chainId, historicBlockHeight)
        } else {
            logger.debug { "getHistoricConfiguration() - Fetching configuration from chain0 (for chain: $chainId and height: $historicBlockHeight)" }
            val dba = DatabaseAccess.of(eContext)
            val blockchainRid = getBlockchainRid(eContext, dba)
            return dataSource.getConfiguration(blockchainRid.data, historicBlockHeight)
        }
    }

    // --------- Private --------

    private fun checkNeedConfChangeViaDataSource(eContext: EContext): Boolean {
        val dba = DatabaseAccess.of(eContext)
        val blockchainRid = getBlockchainRid(eContext, dba)
        val lastSavedBlockHeight = dba.getLastBlockHeight(eContext)
        val activeHeight = getActiveBlocksHeight(eContext, dba)
        val logPrefix = "checkNeedConfChangeViaDataSource(${eContext.chainID})"

        return if (isPcuEnabled()) {
            if (pendingConfigurations.containsKey(eContext.chainID)) {
                logger.debug { "$logPrefix: pendingConfig is being applied, so no need to load a new configuration" }
                false
            } else {
                val res = dataSource.getPendingBlockchainConfiguration(blockchainRid, activeHeight) != null
                logger.debug { "$logPrefix: new pending configuration detected for activeHeight: $activeHeight" }
                res
            }
        } else {
            val nextConfigHeight = dataSource.findNextConfigurationHeight(blockchainRid.data, lastSavedBlockHeight)
            if (nextConfigHeight == null) {
                logger.debug {
                    "$logPrefix: No future configurations found for chain: ${eContext.chainID}, activeHeight: $activeHeight"
                }
            } else if (nextConfigHeight >= activeHeight) {
                logger.debug {
                    "$logPrefix: Closest configurations found at height: " +
                            "$nextConfigHeight for chain: ${eContext.chainID}, activeHeight: $activeHeight."
                }
            } else { // (nextConfigHeight < activeHeight)
                logger.error("$logPrefix: didn't expect to find a future conf at lower height: " +
                        "$nextConfigHeight that our active height: $activeHeight, chain: ${eContext.chainID}")
            }
            nextConfigHeight != null && activeHeight == nextConfigHeight  // Since we are looking for future configs here it's ok to get null back
        }
    }

    override fun findNextConfigurationHeight(eContext: EContext, height: Long): Long? {
        val dba = DatabaseAccess.of(eContext)
        val blockchainRid = getBlockchainRid(eContext, dba)
        return dataSource.findNextConfigurationHeight(blockchainRid.data, height)
    }

    private fun getConfigurationFromDataSource(eContext: EContext): ByteArray? {
        val dba = DatabaseAccess.of(eContext)
        val blockchainRid = getBlockchainRid(eContext, dba)
        val activeHeight = getActiveBlocksHeight(eContext, dba)
        val logPrefix = "getConfigurationFromDataSource(${eContext.chainID})"

        return if (isPcuEnabled()) {
            if (activeHeight == 0L) {
                logger.debug { "$logPrefix: the initial config will be loaded from DataSource" }
                dataSource.getConfiguration(blockchainRid.data, 0L)
            } else {
                val config = dataSource.getPendingBlockchainConfiguration(blockchainRid, activeHeight)
                if (config != null) {
                    logger.debug { "$logPrefix: pending config detected at height: $activeHeight and will be loaded" }
                    pendingConfigurations[eContext.chainID] = PendingBlockchainConfigurationStatus(activeHeight, config)
                    config.fullConfig
                } else { // This branch is chosen after blockchain restarts and if there is no a pending config at activeHeight
                    logger.debug { "$logPrefix: pending config is absent at height: $activeHeight, config will be loaded from DataSource" }
                    dataSource.getConfiguration(blockchainRid.data, activeHeight)
                }
            }
        } else {
            dataSource.getConfiguration(blockchainRid.data, activeHeight)
        }
    }

    private fun getBlockchainRid(eContext: EContext, dba: DatabaseAccess): BlockchainRid {
        val blockchainRid = dba.getBlockchainRid(eContext)
        checkNotNull(blockchainRid) { "Unknown chainId: ${eContext.chainID}" }
        return blockchainRid
    }
}