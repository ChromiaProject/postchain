// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.blockchain

import mu.KLogging
import net.postchain.base.BaseConfigurationDataStore
import net.postchain.base.data.DatabaseAccess
import net.postchain.core.EContext

class ManualBlockchainConfigurationProvider : AbstractBlockchainConfigurationProvider() {

    companion object : KLogging()

    override fun activeBlockNeedsConfigurationChange(eContext: EContext, chainId: Long): Boolean {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        val dba = DatabaseAccess.of(eContext)
        val activeHeight = getActiveBlocksHeight(eContext, dba)
        val configHeight = BaseConfigurationDataStore.findConfigurationHeightForBlock(eContext, activeHeight)

        // Some safety checks
        if (configHeight == null) {
            logger.warn("activeBlockNeedsNewConfig() - Chain: $chainId doesn't have a configuration in DB")
        } else if (configHeight < activeHeight) {
            if (logger.isDebugEnabled) {
                logger.debug("activeBlockNeedsNewConfig() - No need to reload config, since active height: " +
                        "$activeHeight, should still use conf at: $configHeight "
                )
            }
        } else if (configHeight > activeHeight) {
            logger.error("activeBlockNeedsNewConfig() - Why did we find a next config height: " +
                    "$configHeight higher than our active block's height: $activeHeight (chain: $chainId)? " +
                    " Most likely a bug")
        }

        return activeHeight == configHeight
    }

    override fun getPreviousSuccessfulConfiguration(eContext: EContext, chainId: Long): ByteArray? {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        val dba = DatabaseAccess.of(eContext)
        val activeHeight = getActiveBlocksHeight(eContext, dba)
        val currentHeight = activeHeight - 1
        if (currentHeight < 0) {
            logger.warn("getActiveBlocksConfiguration() - Chain: $chainId doesn't have a configuration in DB")
            return null
        }

        val configHeight = BaseConfigurationDataStore.findConfigurationHeightForBlock(eContext, currentHeight)

        return if (configHeight == null) {
            logger.warn("getActiveBlocksConfiguration() - Chain: $chainId doesn't have a configuration in DB")
            null
        } else {
            BaseConfigurationDataStore.getConfigurationData(eContext, configHeight)!!
        }
    }

    override fun getActiveBlockConfiguration(eContext: EContext, chainId: Long): ByteArray? {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        val dba = DatabaseAccess.of(eContext)
        val activeHeight = getActiveBlocksHeight(eContext, dba)
        val configHeight = BaseConfigurationDataStore.findConfigurationHeightForBlock(eContext, activeHeight)

        return if (configHeight == null) {
            logger.warn("getActiveBlocksConfiguration() - Chain: $chainId doesn't have a configuration in DB")
            null
        } else {
            BaseConfigurationDataStore.getConfigurationData(eContext, configHeight)!!
        }
    }

    override fun getHistoricConfigurationHeight(eContext: EContext, chainId: Long, historicBlockHeight: Long): Long? {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        return BaseConfigurationDataStore.findConfigurationHeightForBlock(eContext, historicBlockHeight)
    }

    override fun getHistoricConfiguration(eContext: EContext, chainId: Long, historicBlockHeight: Long): ByteArray? {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        return BaseConfigurationDataStore.getConfigurationData(eContext, historicBlockHeight)
    }

    override fun findNextConfigurationHeight(eContext: EContext, height: Long): Long? {
        return DatabaseAccess.of(eContext).findConfigurationHeightForBlock(eContext, height)
    }
}