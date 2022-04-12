package net.postchain.integrationtest.statemachine

import net.postchain.base.config.BlockchainConfig
import net.postchain.core.TransactionFactory
import net.postchain.devtools.testinfra.TestBlockchainConfiguration
import net.postchain.gtx.GTXModule

class FailableTestBlockchainConfiguration(
        configData: BlockchainConfig,
        module: GTXModule
) : TestBlockchainConfiguration(configData, module) {

    override fun getTransactionFactory(): TransactionFactory = FailedTestTransactionFactory()
//    override fun getTransactionFactory(): TransactionFactory = NotFailedTestTransactionFactory()

}