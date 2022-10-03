// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools.testinfra

import mu.KLogging
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.configuration.BaseBlockchainConfiguration
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.core.TransactionFactory
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXModuleAwareness

open class TestBlockchainConfiguration(
        configData: BlockchainConfigurationData,
        override val module: GTXModule
) : BaseBlockchainConfiguration(configData), GTXModuleAwareness {

    open val transactionFactory = TestTransactionFactory()

    companion object : KLogging()

    override fun getTransactionFactory(): TransactionFactory {
        return transactionFactory
    }

    override fun makeBBExtensions(): List<BaseBlockBuilderExtension> {
        return module.makeBlockBuilderExtensions()
    }
}