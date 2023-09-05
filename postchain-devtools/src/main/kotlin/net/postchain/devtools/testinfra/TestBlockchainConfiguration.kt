// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools.testinfra

import mu.KLogging
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.base.configuration.BlockchainConfigurationOptions
import net.postchain.core.BlockchainContext
import net.postchain.core.TransactionFactory
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.gtx.GTXModule

open class TestBlockchainConfiguration(
        configData: BlockchainConfigurationData,
        cryptoSystem: CryptoSystem,
        partialContext: BlockchainContext,
        blockSigMaker: SigMaker,
        module: GTXModule,
        blockchainConfigurationOptions: BlockchainConfigurationOptions = BlockchainConfigurationOptions.DEFAULT
) : GTXBlockchainConfiguration(configData, cryptoSystem, partialContext, blockSigMaker, module, blockchainConfigurationOptions) {

    open val transactionFactory = TestTransactionFactory()

    companion object : KLogging()

    override fun getTransactionFactory(): TransactionFactory {
        return transactionFactory
    }

    override fun makeBBExtensions(): List<BaseBlockBuilderExtension> {
        return module.makeBlockBuilderExtensions()
    }
}