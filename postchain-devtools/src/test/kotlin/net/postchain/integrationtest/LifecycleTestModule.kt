package net.postchain.integrationtest

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.EContext
import net.postchain.core.Transactor
import net.postchain.gtv.Gtv
import net.postchain.gtx.GTXModule
import net.postchain.gtx.PostchainContextAware
import net.postchain.gtx.data.ExtOpData
import net.postchain.gtx.special.GTXSpecialTxExtension

class LifecycleTestModule : GTXModule, PostchainContextAware {
    companion object : KLogging()

    var hasDb = false
    var hasContext = false
    var hasSTX = false
    var hasShutdown = false

    init {
        logger.info { "LifecycleTestModule instance created" }
    }

    override fun makeTransactor(opData: ExtOpData): Transactor = throw NotImplementedError("Transactor creation not implemented")

    override fun getOperations(): Set<String> = setOf()

    override fun getQueries(): Set<String> = setOf()

    override fun query(ctxt: EContext, name: String, args: Gtv): Gtv = throw NotImplementedError("Query execution not implemented")

    override fun initializeDB(ctx: EContext) {
        logger.info { "Initializing database" }
        hasDb = true
    }

    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> = listOf()

    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> {
        logger.info { "Getting special transaction extensions" }
        hasSTX = true
        return listOf()
    }

    override fun initializeContext(configuration: BlockchainConfiguration, postchainContext: PostchainContext, ctx: EContext) {
        logger.info { "Initializing context" }
        hasContext = true
    }

    override fun shutdown() {
        logger.info { "Shutting down" }
        hasShutdown = true
    }
}
