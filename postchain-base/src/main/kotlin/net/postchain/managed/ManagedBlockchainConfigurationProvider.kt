// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.managed

import mu.KLogging
import net.postchain.base.data.DatabaseAccess
import net.postchain.config.blockchain.AbstractBlockchainConfigurationProvider
import net.postchain.config.blockchain.ManualBlockchainConfigurationProvider
import net.postchain.core.EContext

/**
 * A complicated mix of local (=DB) reads and external (=Chain0 API) reads.
 * Currently we allow local configuration to override the external configuration, but this is tricky
 * (see below for more).
 * If you need to use local configuration, the recommended way is to switch to manual mode (instead doing the
 * override in managed mode).
 */
open class ManagedBlockchainConfigurationProvider : AbstractBlockchainConfigurationProvider() {

    lateinit var dataSource: ManagedNodeDataSource
    private val systemProvider = ManualBlockchainConfigurationProvider() // Used mainly to access Chain0 (we don't want to use Chain0 to check it's own config changes, too strange)

    companion object : KLogging()

    /**
     * We read the config in this way:
     * 1. check the local (DB) setting first, if nothing found
     * 2. check the [ManagedNodeDataSource] for the config
     *
     * Note:
     * This means that even for managed mode, we allow manual override of configurations at specific heights.
     * This is a very tricky subject, since a manual override can cause the chain to fork!
     */
    override fun getActiveBlocksConfiguration(eContext: EContext, chainId: Long): ByteArray? {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        return if (chainId == 0L) {
            systemProvider.getActiveBlocksConfiguration(eContext, chainId)
        } else {
            val localOverride = systemProvider.getActiveBlocksConfiguration(eContext, chainId)
            return if (localOverride != null) {
                logger.warn("getConfiguration() - You are about to use local configuration to override the " +
                        "configuration of chain0 (for chain: $chainId)! Very dangerous")
                localOverride
            } else {
                // No override, fetch from chain0
                if (logger.isDebugEnabled) {
                    logger.debug("getConfiguration() - Fetching configuration from chain0 (for chain: $chainId)")
                }
                if (::dataSource.isInitialized) {
                    getConfigurationFromDataSource(eContext)
                } else {
                    throw IllegalStateException("Using managed blockchain configuration provider before it's properly initialized")
                }
            }
        }
    }

    /**
     * Note:
     * We only check the [ManagedNodeDataSource] for valid heights where new configurations can be found, and will
     * therefore ignore any override configuration put in the "configuration" table for other heights
     * (i.e. pretend they don't exist).
     * Reason: override in managed mode is hard enough as it is, and it's doubtful if it should be allowed.
     */
    override fun activeBlockNeedsConfigurationChange(eContext: EContext, chainId: Long): Boolean {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        return if (chainId == 0L) {
            systemProvider.activeBlockNeedsConfigurationChange(eContext, chainId)
        } else {
            if (::dataSource.isInitialized) {
                checkNeedConfChangeViaDataSource(eContext)
            } else {
                throw IllegalStateException("Using managed blockchain configuration provider before it's properly initialized")
            }
        }
    }

    /**
     * Same principle for "historic" as for "current"
     */
    override fun getHistoricConfigurationHeight(eContext: EContext, chainId: Long, historicBlockHeight: Long): Long? {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        return if (chainId == 0L) {
            systemProvider.getHistoricConfigurationHeight(eContext, chainId, historicBlockHeight)
        } else {
            // We ignore local setting, go to Chain0 directly
            val dba = DatabaseAccess.of(eContext)
            getHeightAwareConfChangeHeightViaDataSource(eContext, dba, historicBlockHeight)
        }
    }

    /**
     * Same principle for "historic" as for "current"
     */
    override fun getHistoricConfiguration(eContext: EContext, chainId: Long, historicBlockHeight: Long): ByteArray? {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        return if (chainId == 0L) {
            systemProvider.getHistoricConfiguration(eContext, chainId, historicBlockHeight)
        } else {
            val localOverride = systemProvider.getHistoricConfiguration(eContext, chainId, historicBlockHeight)
            return if (localOverride != null) {
                if (logger.isDebugEnabled) {
                    logger.debug("getHistoricConfiguration() - Found local configuration to override the configuration of chain0 (for chain: $chainId and height: $historicBlockHeight).")
                }
                localOverride
            } else {
                // No override, fetch from chain0
                if (logger.isDebugEnabled) {
                    logger.debug("getHistoricConfiguration() - Fetching configuration from chain0 (for chain: $chainId and height: $historicBlockHeight)")
                }
                val dba = DatabaseAccess.of(eContext)
                return getHeightAwareConfigFromDatasource(eContext, dba, historicBlockHeight)
            }
        }
    }

    // --------- Private --------

    private fun checkNeedConfChangeViaDataSource(eContext: EContext): Boolean {
        val dba = DatabaseAccess.of(eContext)
        val activeHeight = getActiveBlocksHeight(eContext, dba)
        val nextConfigHeight = getHeightAwareConfChangeHeightViaDataSource(eContext, dba, activeHeight) //

        if (nextConfigHeight == null) {
            if (logger.isDebugEnabled) {
                logger.debug("checkNeedConfChangeViaDataSource() - no future configurations found for " +
                        "chain: ${eContext.chainID} , activeHeight : $activeHeight"
                )
            }
        } else if (nextConfigHeight > activeHeight) {
            if (logger.isDebugEnabled) {
                logger.debug("checkNeedConfChangeViaDataSource() - Closest configurations found at height: " +
                        "$nextConfigHeight for chain: ${eContext.chainID}, activeHeight : $activeHeight."
                )
            }
        } else if (nextConfigHeight < activeHeight) {
            logger.error("checkNeedConfChangeViaDataSource() - didn't expect to find a future conf at lower height: " +
                    "$nextConfigHeight that our active height: $activeHeight, chain: ${eContext.chainID}")
        }

        return nextConfigHeight != null && activeHeight == nextConfigHeight  // Since we are looking for future configs here it's ok to get null back
    }

    /**
     * Note: Here we use the [ManagedNodeDataSource] that looks for FUTURE config changes (not like manual DB query)
     *       meaning we might get "null" back as a valid answer
     */
    private fun getHeightAwareConfChangeHeightViaDataSource(eContext: EContext, dba: DatabaseAccess, activeHeight: Long): Long? {
        val blockchainRID = dba.getBlockchainRid(eContext)
        // Skip override, we go directly to Chain0 to check
        val savedBlockHeight = activeHeight - 1 // Tricky part, for [ManagedNodeDataSource] we must send the last saved block height
        return dataSource.findNextConfigurationHeight(blockchainRID!!.data, savedBlockHeight)
    }

    override fun findNextConfigurationHeight(eContext: EContext, height: Long): Long? {
        val db = DatabaseAccess.of(eContext)
        val brid = db.getBlockchainRid(eContext)
        return dataSource.findNextConfigurationHeight(brid!!.data, height)
    }

    override fun isDataSourceReady(): Boolean = true

    private fun getConfigurationFromDataSource(eContext: EContext): ByteArray? {
        val dba = DatabaseAccess.of(eContext)
        val activeHeight = getActiveBlocksHeight(eContext, dba)
        return getHeightAwareConfigFromDatasource(eContext, dba, activeHeight)
    }

    private fun getHeightAwareConfigFromDatasource(eContext: EContext, dba: DatabaseAccess, height: Long): ByteArray? {
        val blockchainRID = dba.getBlockchainRid(eContext)
        return dataSource.getConfiguration(blockchainRID!!.data, height)
    }

}