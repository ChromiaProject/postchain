package net.postchain.devtools.mminfra

import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.configuration.BaseBlockchainConfiguration
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.extension.FailedConfigurationHashBlockBuilderExtension
import net.postchain.core.BlockchainContext
import net.postchain.core.EContext
import net.postchain.core.TransactionFactory
import net.postchain.core.TransactionQueue
import net.postchain.core.block.BlockBuilder
import net.postchain.core.block.BlockBuildingStrategy
import net.postchain.core.block.BlockQueries
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.devtools.OnDemandBlockBuildingStrategy
import net.postchain.devtools.testinfra.TestTransactionFactory
import net.postchain.managed.ManagedNodeDataSource
import net.postchain.managed.config.ManagedDataSourceAware
import net.postchain.managed.config.faulty.LocalFaultyConfigurationReportHelper
import java.time.Clock

class TestBlockchainConfiguration(
        data: BlockchainConfigurationData,
        partialContext: BlockchainContext,
        blockSigMaker: SigMaker,
        override var dataSource: ManagedNodeDataSource
) : BaseBlockchainConfiguration(data, Secp256K1CryptoSystem(), partialContext, blockSigMaker), ManagedDataSourceAware {

    override fun getTransactionFactory(): TransactionFactory {
        return TestTransactionFactory()
    }

    override fun getBlockBuildingStrategy(blockQueries: BlockQueries, txQueue: TransactionQueue): BlockBuildingStrategy {
        return OnDemandBlockBuildingStrategy(blockStrategyConfig, blockQueries, txQueue, Clock.systemUTC())
    }

    override fun makeBlockBuilder(ctx: EContext, isSyncing: Boolean, extraExtensions: List<BaseBlockBuilderExtension>): BlockBuilder {
        val height = DatabaseAccess.of(ctx).getLastBlockHeight(ctx) + 1
        val failedConfigToReport = dataSource.getFaultyBlockchainConfiguration(blockchainRid, height)
                ?: LocalFaultyConfigurationReportHelper.getFaultyConfigToReportAtHeight(ctx, dataSource, blockchainRid, height)

        return if (failedConfigToReport != null) {
            super.makeBlockBuilder(ctx, isSyncing, extraExtensions + listOf(FailedConfigurationHashBlockBuilderExtension(failedConfigToReport)))
        } else {
            super.makeBlockBuilder(ctx, isSyncing, extraExtensions)
        }
    }
}
