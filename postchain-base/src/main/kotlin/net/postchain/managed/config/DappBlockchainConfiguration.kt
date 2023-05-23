package net.postchain.managed.config

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.extension.FailedConfigurationHashBlockBuilderExtension
import net.postchain.core.EContext
import net.postchain.core.block.BlockBuilder
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.managed.ManagedNodeDataSource

open class DappBlockchainConfiguration(
        private val configuration: GTXBlockchainConfiguration,
        dataSource: ManagedNodeDataSource
) : ManagedBlockchainConfiguration(configuration, dataSource) {
    override fun makeBlockBuilder(ctx: EContext, extraExtensions: List<BaseBlockBuilderExtension>): BlockBuilder {
        val height = DatabaseAccess.of(ctx).getLastBlockHeight(ctx) + 1
        val failedConfigToReport = dataSource.getFaultyBlockchainConfiguration(blockchainRid, height)
                ?: getFaultyConfigToReportAtHeight(ctx, height)

        return if (failedConfigToReport != null) {
            configuration.makeBlockBuilder(ctx, extraExtensions + listOf(FailedConfigurationHashBlockBuilderExtension(failedConfigToReport)))
        } else {
            configuration.makeBlockBuilder(ctx, extraExtensions)
        }
    }

    private fun getFaultyConfigToReportAtHeight(ctx: EContext, height: Long): ByteArray? {
        val faultyConfiguration = DatabaseAccess.of(ctx).getFaultyConfiguration(ctx)
        return if (faultyConfiguration != null && faultyConfiguration.reportAtHeight == height) {
            faultyConfiguration.configHash.data
        } else null
    }
}
