package net.postchain.managed.config.faulty

import net.postchain.base.data.DatabaseAccess
import net.postchain.common.BlockchainRid
import net.postchain.core.EContext
import net.postchain.managed.ManagedNodeDataSource

object LocalFaultyConfigurationReportHelper {

    fun getFaultyConfigToReportAtHeight(ctx: EContext, dataSource: ManagedNodeDataSource, blockchainRid: BlockchainRid, height: Long): ByteArray? {
        val faultyConfiguration = DatabaseAccess.of(ctx).getFaultyConfiguration(ctx)
        return if (faultyConfiguration != null && faultyConfiguration.reportAtHeight == height
                && dataSource.getPendingBlockchainConfiguration(blockchainRid, height).any { it.configHash == faultyConfiguration.configHash }) {
            faultyConfiguration.configHash.data
        } else null
    }
}