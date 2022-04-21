// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.devtools.testinfra

import mu.KLogging
import net.postchain.base.BlockchainConfigurationData
import net.postchain.base.data.BaseBlockchainConfiguration
import net.postchain.core.EContext
import net.postchain.core.TransactionFactory
import net.postchain.gtx.GTXModule

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
}