// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools.testinfra

import mu.KLogging
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.configuration.BaseBlockchainConfiguration
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.gtx.GTXModule
import net.postchain.core.EContext
import net.postchain.core.TransactionFactory

open class TestBlockchainConfiguration(
        configData: BlockchainConfigurationData,
        val module: GTXModule
) : BaseBlockchainConfiguration(configData) {

    open val transactionFactory = TestTransactionFactory()

    companion object : KLogging()

    override fun getTransactionFactory(): TransactionFactory {
        return transactionFactory
    }

    override fun initializeDB(ctx: EContext) {
        super.initializeDB(ctx)
        logger.debug{ "++ TEST ONLY ++: Running TestBlockchainConfiguration - means DB for modules NOT initialized! ++ TEST ONLY ++" }
    }

    override fun makeBBExtensions(): List<BaseBlockBuilderExtension> {
        return module.makeBlockBuilderExtensions()
    }
}