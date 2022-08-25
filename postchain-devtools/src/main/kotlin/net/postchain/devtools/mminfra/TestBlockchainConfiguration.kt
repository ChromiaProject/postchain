package net.postchain.devtools.mminfra

import net.postchain.base.configuration.BaseBlockchainConfiguration
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.core.TransactionFactory
import net.postchain.core.TransactionQueue
import net.postchain.core.block.BlockBuildingStrategy
import net.postchain.core.block.BlockQueries
import net.postchain.devtools.OnDemandBlockBuildingStrategy
import net.postchain.devtools.testinfra.TestTransactionFactory

class TestBlockchainConfiguration(data: BlockchainConfigurationData) : BaseBlockchainConfiguration(data) {
    override fun getTransactionFactory(): TransactionFactory {
        return TestTransactionFactory()
    }

    override fun getBlockBuildingStrategy(blockQueries: BlockQueries, txQueue: TransactionQueue): BlockBuildingStrategy {
        return OnDemandBlockBuildingStrategy(blockStrategyConfig, blockQueries, txQueue)
    }
}