// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.blockchain

import mu.KLogging
import net.postchain.base.data.DatabaseAccess
import net.postchain.core.EContext

class ManualBlockchainConfigurationProvider : AbstractBlockchainConfigurationProvider() {

    companion object : KLogging()

    override fun activeBlockNeedsConfigurationChange(eContext: EContext, chainId: Long, isSigner: Boolean): Boolean {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        val dba = DatabaseAccess.of(eContext)
        val activeHeight = getActiveBlocksHeight(eContext, dba)
        val configHeight = dba.findConfigurationHeightForBlock(eContext, activeHeight)

        // Some safety checks
        if (configHeight == null) {
            logger.warn("activeBlockNeedsNewConfig() - Chain: $chainId doesn't have a configuration in DB")
        } else if (configHeight < activeHeight) {
            logger.debug {
                "activeBlockNeedsNewConfig() - No need to reload config, since active height: " +
                        "$activeHeight, should still use conf at: $configHeight "
            }
        } else if (configHeight > activeHeight) {
            logger.error("activeBlockNeedsNewConfig() - Why did we find a next config height: " +
                    "$configHeight higher than our active block's height: $activeHeight (chain: $chainId)? " +
                    " Most likely a bug")
        }

        return activeHeight == configHeight
    }

    override fun getActiveBlocksConfiguration(eContext: EContext, chainId: Long, loadNextPendingConfig: Boolean): ByteArray? {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        val dba = DatabaseAccess.of(eContext)
        val activeHeight = getActiveBlocksHeight(eContext, dba)
        val configHeight = dba.findConfigurationHeightForBlock(eContext, activeHeight)

        return if (configHeight == null) {
            logger.debug("getActiveBlocksConfiguration() - Chain: $chainId doesn't have a configuration in DB")
            null
        } else {
            dba.getConfigurationData(eContext, configHeight)!!
        }
    }

    override fun getHistoricConfigurationHeight(eContext: EContext, chainId: Long, historicBlockHeight: Long): Long? {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        return DatabaseAccess.of(eContext).findConfigurationHeightForBlock(eContext, historicBlockHeight)
    }

    override fun getHistoricConfiguration(eContext: EContext, chainId: Long, historicBlockHeight: Long): ByteArray? {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        return DatabaseAccess.of(eContext).getConfigurationData(eContext, historicBlockHeight)
    }
}