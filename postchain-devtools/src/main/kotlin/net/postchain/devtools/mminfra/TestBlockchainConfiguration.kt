package net.postchain.devtools.mminfra

import net.postchain.base.configuration.BaseBlockchainConfiguration
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.core.TransactionFactory
import net.postchain.core.TransactionQueue
import net.postchain.core.block.BlockBuildingStrategy
import net.postchain.core.block.BlockQueries
import net.postchain.devtools.OnDemandBlockBuildingStrategy
import net.postchain.devtools.testinfra.TestTransactionFactory
import net.postchain.managed.ManagedNodeDataSource
import net.postchain.managed.config.ManagedDataSourceAwareness

class TestBlockchainConfiguration(
        data: BlockchainConfigurationData,
        override var dataSource: ManagedNodeDataSource
) : BaseBlockchainConfiguration(data), ManagedDataSourceAwareness {

    override fun getTransactionFactory(): TransactionFactory {
        return TestTransactionFactory()
    }

    override fun getBlockBuildingStrategy(blockQueries: BlockQueries, txQueue: TransactionQueue): BlockBuildingStrategy {
        return OnDemandBlockBuildingStrategy(blockStrategyConfig, blockQueries, txQueue)
    }
}