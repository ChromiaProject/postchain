package net.postchain.devtools

import mu.KLogging
import net.postchain.base.data.DatabaseAccess
import net.postchain.config.blockchain.AbstractBlockchainConfigurationProvider
import net.postchain.core.EContext
import net.postchain.devtools.utils.ChainUtil
import net.postchain.managed.ManagedNodeDataSource

/**
 * This test conf provider can emulate managed mode, but is using a stubbed [ManagedNodeDataSource] that can be
 * replaced. This version is a bit cruder though, we don't have a special handling of chain0
 * like the real managed mode provider.
 *
 * (broken out to here from Kalle's code, thought it could be re-used)
 */
class MockBlockchainConfigurationProvider():
    AbstractBlockchainConfigurationProvider() {   // Using the abstract class means we are using "real" ICMF for test, see no reason not to.

    lateinit var mockDataSource: ManagedNodeDataSource

    companion object: KLogging()

    override fun getActiveBlocksConfiguration(eContext: EContext, chainId: Long): ByteArray? {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        val dba = DatabaseAccess.of(eContext)
        val activeHeight = this.getActiveBlocksHeight(eContext, dba)
        return mockDataSource.getConfiguration(ChainUtil.ridOf(chainId).data, activeHeight)
    }

    override fun activeBlockNeedsConfigurationChange(eContext: EContext, chainId: Long): Boolean {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        val dba = DatabaseAccess.of(eContext)
        val activeHeight = getActiveBlocksHeight(eContext, dba)
        val lastSavedHeight = activeHeight - 1
        val blockchainRid = ChainUtil.ridOf(chainId)
        val nextConfigHeight = mockDataSource.findNextConfigurationHeight(blockchainRid.data, lastSavedHeight) // Don't use active height here
        logger.debug("needsConfigurationChange() - active height: $activeHeight, next conf at: $nextConfigHeight")
        return nextConfigHeight != null && activeHeight == nextConfigHeight
    }

    /**
     * Same principle for "historic" as for "current"
     */
    override fun getHistoricConfigurationHeight(eContext: EContext, chainId: Long, historicBlockHeight: Long): Long? {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        val blockchainRid = ChainUtil.ridOf(chainId)
        val nextConfigHeight = mockDataSource.findNextConfigurationHeight(blockchainRid.data, historicBlockHeight)
        logger.debug("getHistoricConfigurationHeight() - checking historic height: $historicBlockHeight, next conf at: $nextConfigHeight")
        return nextConfigHeight
    }

    /**
     * Same principle for "historic" as for "current"
     */
    override fun getHistoricConfiguration(eContext: EContext, chainId: Long, historicBlockHeight: Long): ByteArray? {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        logger.debug("getHistoricConfiguration() - Fetching configuration from chain0 (for chain: $chainId and height: $historicBlockHeight)")
        return mockDataSource.getConfiguration(ChainUtil.ridOf(chainId).data, historicBlockHeight)
    }

    override fun findNextConfigurationHeight(eContext: EContext, height: Long): Long? {
        TODO("Not implemented yet")
    }

    override fun isDataSourceReady(): Boolean = true
}

