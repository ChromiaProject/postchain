package net.postchain.devtools

import mu.KLogging
import net.postchain.base.data.DatabaseAccess
import net.postchain.config.blockchain.ConfigurationChangeCheckResult
import net.postchain.core.EContext
import net.postchain.devtools.utils.ChainUtil
import net.postchain.managed.ManagedBlockchainConfigurationProvider
import net.postchain.managed.ManagedNodeDataSource

/**
 * This test conf provider can emulate managed mode, but is using a stubbed [ManagedNodeDataSource] that can be
 * replaced. This version is a bit cruder though, we don't have a special handling of chain0
 * like the real managed mode provider.
 *
 * (broken out to here from Kalle's code, thought it could be re-used)
 */
class MockBlockchainConfigurationProvider :
        ManagedBlockchainConfigurationProvider() {   // Using the abstract class means we are using "real" ICMF for test, see no reason not to.

    companion object : KLogging()

    override fun getActiveBlockConfiguration(eContext: EContext, chainId: Long, pendingConfigHashToLoad: ByteArray?): ByteArray? {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        return getConfigurationFromDataSource(eContext, pendingConfigHashToLoad)
    }

    override fun activeBlockNeedsConfigurationChange(eContext: EContext, chainId: Long, checkPendingConfigs: Boolean): ConfigurationChangeCheckResult {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        return checkNeedConfChangeViaDataSource(eContext, checkPendingConfigs)
    }

    override fun getHistoricConfigurationHeight(eContext: EContext, chainId: Long, historicBlockHeight: Long): Long? {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        val blockchainRid = ChainUtil.ridOf(chainId)
        var height = -1L
        while (height < historicBlockHeight) {
            val nextHeight = dataSource.findNextConfigurationHeight(blockchainRid.data, height + 1)
            if (nextHeight == null || nextHeight > historicBlockHeight) {
                return height
            } else height = nextHeight
        }

        return height
    }

    override fun getBlockchainRid(eContext: EContext, dba: DatabaseAccess) = ChainUtil.ridOf(eContext.chainID)

    /**
     * Same principle for "historic" as for "current"
     */
    override fun getHistoricConfiguration(eContext: EContext, chainId: Long, historicBlockHeight: Long): ByteArray? {
        requireChainIdToBeSameAsInContext(eContext, chainId)

        logger.debug("getHistoricConfiguration() - Fetching configuration from chain0 (for chain: $chainId and height: $historicBlockHeight)")
        return dataSource.getConfiguration(ChainUtil.ridOf(chainId).data, historicBlockHeight)
    }
}

