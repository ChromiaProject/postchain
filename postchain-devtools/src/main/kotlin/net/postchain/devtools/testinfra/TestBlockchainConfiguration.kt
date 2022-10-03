// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools.testinfra

import mu.KLogging
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.core.TransactionFactory
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.gtx.GTXModule

open class TestBlockchainConfiguration(
        configData: BlockchainConfigurationData,
        override val module: GTXModule
) : GTXBlockchainConfiguration(configData, module) {

    open val transactionFactory = TestTransactionFactory()

    companion object : KLogging()

    override fun getTransactionFactory(): TransactionFactory {
        return transactionFactory
    }

    override fun makeBBExtensions(): List<BaseBlockBuilderExtension> {
        return module.makeBlockBuilderExtensions()
    }
}