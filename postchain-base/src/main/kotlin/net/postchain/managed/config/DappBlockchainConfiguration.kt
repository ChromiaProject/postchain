package net.postchain.managed.config

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.extension.FailedConfigurationHashBlockBuilderExtension
import net.postchain.core.EContext
import net.postchain.core.block.BlockBuilder
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.managed.ManagedNodeDataSource
import net.postchain.managed.config.faulty.LocalFaultyConfigurationReportHelper

open class DappBlockchainConfiguration(
        configuration: GTXBlockchainConfiguration,
        dataSource: ManagedNodeDataSource
) : ManagedBlockchainConfiguration(configuration, dataSource) {
    override fun makeBlockBuilder(ctx: EContext, isSyncing: Boolean, extraExtensions: List<BaseBlockBuilderExtension>): BlockBuilder {
        val height = DatabaseAccess.of(ctx).getLastBlockHeight(ctx) + 1
        val failedConfigToReport = dataSource.getFaultyBlockchainConfiguration(blockchainRid, height)
                ?: LocalFaultyConfigurationReportHelper.getFaultyConfigToReportAtHeight(ctx, dataSource, blockchainRid, height)

        return if (failedConfigToReport != null) {
            configuration.makeBlockBuilder(ctx, isSyncing, extraExtensions + listOf(FailedConfigurationHashBlockBuilderExtension(failedConfigToReport)))
        } else {
            configuration.makeBlockBuilder(ctx, isSyncing, extraExtensions)
        }
    }
}
