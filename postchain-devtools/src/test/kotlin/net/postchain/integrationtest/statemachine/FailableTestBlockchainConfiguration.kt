package net.postchain.integrationtest.statemachine

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.base.configuration.BlockchainConfigurationOptions
import net.postchain.core.BlockchainContext
import net.postchain.core.TransactionFactory
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.devtools.testinfra.TestBlockchainConfiguration
import net.postchain.gtx.GTXModule

class FailableTestBlockchainConfiguration(
        configData: BlockchainConfigurationData,
        cryptoSystem: CryptoSystem,
        partialContext: BlockchainContext,
        blockSigMaker: SigMaker,
        module: GTXModule,
        blockchainConfigurationOptions: BlockchainConfigurationOptions
) : TestBlockchainConfiguration(configData, cryptoSystem, partialContext, blockSigMaker, module, blockchainConfigurationOptions) {

    override fun getTransactionFactory(): TransactionFactory = FailedTestTransactionFactory()
//    override fun getTransactionFactory(): TransactionFactory = NotFailedTestTransactionFactory()

}