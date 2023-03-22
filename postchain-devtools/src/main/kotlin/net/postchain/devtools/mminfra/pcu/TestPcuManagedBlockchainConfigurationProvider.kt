package net.postchain.devtools.mminfra.pcu

import net.postchain.base.data.DatabaseAccess
import net.postchain.core.EContext
import net.postchain.devtools.utils.ChainUtil
import net.postchain.managed.ManagedBlockchainConfigurationProvider

class TestPcuManagedBlockchainConfigurationProvider : ManagedBlockchainConfigurationProvider() {

    override fun isPcuEnabled() = true

    override fun getActiveBlocksConfiguration(eContext: EContext, chainId: Long): ByteArray? {
        requireChainIdToBeSameAsInContext(eContext, chainId)
        val dba = DatabaseAccess.of(eContext)
        val activeHeight = this.getActiveBlocksHeight(eContext, dba)

        // All chain0 configs and chainX initial config are taken from MockManagedDataSource
        // Other chainX configs are taken from "D1::pending_config"
        return if (chainId == 0L || activeHeight == 0L) {
            dataSource.getConfiguration(ChainUtil.ridOf(chainId).data, activeHeight)
        } else {
            super.getActiveBlocksConfiguration(eContext, chainId)
        }
    }

    override fun activeBlockNeedsConfigurationChange(eContext: EContext, chainId: Long): Boolean {
        requireChainIdToBeSameAsInContext(eContext, chainId)
        return if (chainId == 0L) {
            val dba = DatabaseAccess.of(eContext)
            val activeHeight = getActiveBlocksHeight(eContext, dba)
            val lastSavedHeight = activeHeight - 1
            val blockchainRid = ChainUtil.ridOf(chainId)
            val nextConfigHeight = dataSource.findNextConfigurationHeight(blockchainRid.data, lastSavedHeight)
            logger.debug("needsConfigurationChange() - active height: $activeHeight, next conf at: $nextConfigHeight")
            nextConfigHeight != null && activeHeight == nextConfigHeight
        } else {
            super.activeBlockNeedsConfigurationChange(eContext, chainId)
        }
    }

    override fun getHistoricConfigurationHeight(eContext: EContext, chainId: Long, historicBlockHeight: Long): Long? {
        requireChainIdToBeSameAsInContext(eContext, chainId)
        val blockchainRid = ChainUtil.ridOf(chainId)
        val nextConfigHeight = dataSource.findNextConfigurationHeight(blockchainRid.data, historicBlockHeight)
        logger.debug("getHistoricConfigurationHeight() - checking historic height: $historicBlockHeight, next conf at: $nextConfigHeight")
        return nextConfigHeight
    }

    override fun getHistoricConfiguration(eContext: EContext, chainId: Long, historicBlockHeight: Long): ByteArray? {
        requireChainIdToBeSameAsInContext(eContext, chainId)
        logger.debug("getHistoricConfiguration() - Fetching configuration from chain0 (for chain: $chainId and height: $historicBlockHeight)")
        return dataSource.getConfiguration(ChainUtil.ridOf(chainId).data, historicBlockHeight)
    }

    override fun isManagedDatasourceReady(eContext: EContext): Boolean {
        val dba = DatabaseAccess.of(eContext)
        val activeHeight = getActiveBlocksHeight(eContext, dba)
        return if (eContext.chainID == 0L || activeHeight == 0L) {
            true
        } else {
            super.isManagedDatasourceReady(eContext)
        }
    }
}