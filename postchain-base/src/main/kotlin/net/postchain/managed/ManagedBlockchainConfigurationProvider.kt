// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.managed

import mu.KLogging
import net.postchain.base.data.DatabaseAccess
import net.postchain.common.BlockchainRid
import net.postchain.config.blockchain.AbstractBlockchainConfigurationProvider
import net.postchain.config.blockchain.ManualBlockchainConfigurationProvider
import net.postchain.core.EContext

open class ManagedBlockchainConfigurationProvider : AbstractBlockchainConfigurationProvider() {

    protected lateinit var dataSource: ManagedNodeDataSource

    // Used to access Chain0 configs which are preloaded and validated in ManagedBlockchainProcessManager.preloadChain0Configuration().
    private val localProvider = ManualBlockchainConfigurationProvider()

    companion object : KLogging()

    protected open fun isPcuEnabled() = false

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

    open fun isPendingBlockchainConfigurationApproved(eContext: EContext): Boolean {
        return if (isPcuEnabled()) {
            val dba = DatabaseAccess.of(eContext)
            val blockchainRid = getBlockchainRid(eContext, dba)
            val activeHeight = getActiveBlocksHeight(eContext, dba)
            dataSource.isPendingBlockchainConfigurationApproved(blockchainRid, activeHeight)
        } else {
            true
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

        return if (isPcuEnabled()) {
            val nextConfigHeight = dataSource.findNextConfigurationHeight(blockchainRid.data, lastSavedBlockHeight)
            if (nextConfigHeight == null) {
                logger.debug {
                    "checkNeedConfChangeViaDataSource() - no future configurations found for " +
                            "chain: ${eContext.chainID}, activeHeight: $activeHeight"
                }
            } else if (nextConfigHeight >= activeHeight) {
                logger.debug {
                    "checkNeedConfChangeViaDataSource() - Closest configurations found at height: " +
                            "$nextConfigHeight for chain: ${eContext.chainID}, activeHeight: $activeHeight."
                }
            } else { // (nextConfigHeight < activeHeight)
                logger.error("checkNeedConfChangeViaDataSource() - didn't expect to find a future conf at lower height: " +
                        "$nextConfigHeight that our active height: $activeHeight, chain: ${eContext.chainID}")
            }
            nextConfigHeight != null && activeHeight == nextConfigHeight  // Since we are looking for future configs here it's ok to get null back
        } else {
            dataSource.getPendingBlockchainConfiguration(blockchainRid, activeHeight) != null
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
        return if (isPcuEnabled()) {
            dataSource.getConfiguration(blockchainRid.data, activeHeight)
        } else {
            dataSource.getPendingBlockchainConfiguration(blockchainRid, activeHeight)
        }
    }

    private fun getBlockchainRid(eContext: EContext, dba: DatabaseAccess): BlockchainRid {
        val blockchainRid = dba.getBlockchainRid(eContext)
        checkNotNull(blockchainRid) { "Unknown chainId: ${eContext.chainID}" }
        return blockchainRid
    }
}