package net.postchain.integrationtest

import assertk.assertThat
import assertk.assertions.isTrue
import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.SpecialTransactionPosition
import net.postchain.common.BlockchainRid
import net.postchain.core.BlockEContext
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainProcess
import net.postchain.core.BlockchainProcessConnectable
import net.postchain.core.EContext
import net.postchain.core.Transactor
import net.postchain.crypto.CryptoSystem
import net.postchain.gtv.Gtv
import net.postchain.gtx.GTXModule
import net.postchain.gtx.PostchainContextAware
import net.postchain.gtx.data.ExtOpData
import net.postchain.gtx.data.OpData
import net.postchain.gtx.special.GTXSpecialTxExtension

class LifecycleTestModule : GTXModule, PostchainContextAware {
    companion object : KLogging()

    var hasDb = false
    var hasContext = false
    var hasSTX = false
    var hasShutdown = false

    var hasExtInit = false
    var hasExtRelevantOps = false
    var hasExtConnect = false
    var hasExtDisconnect = false

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

    override fun initializeContext(configuration: BlockchainConfiguration, postchainContext: PostchainContext, ctx: EContext) {
        assertThat(hasDb).isTrue()
        logger.info { "Initializing context" }
        hasContext = true
    }

    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> {
        assertThat(hasContext).isTrue()
        logger.info { "Getting special transaction extensions" }
        hasSTX = true
        return listOf(LifecycleTestExtension())
    }

    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> {
        logger.info { "Making block builder extensions" }
        return listOf()
    }

    override fun shutdown() {
        logger.info { "Shutting down" }
        hasShutdown = true
    }

    inner class LifecycleTestExtension : GTXSpecialTxExtension, BlockchainProcessConnectable {
        override fun init(module: GTXModule, chainID: Long, blockchainRID: BlockchainRid, cs: CryptoSystem) {
            logger.info("ext init")
            hasExtInit = true
        }

        override fun getRelevantOps(): Set<String> {
            logger.info("ext getRelevantOps")
            hasExtRelevantOps = true
            return setOf()
        }

        override fun connectProcess(process: BlockchainProcess) {
            logger.info("ext connectProcess")
            hasExtConnect = true
        }

        override fun needsSpecialTransaction(position: SpecialTransactionPosition): Boolean {
            logger.info("ext needsSpecialTransaction")
            return false
        }

        override fun createSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext): List<OpData> {
            logger.info("ext createSpecialOperations")
            return listOf()
        }

        override fun validateSpecialOperations(position: SpecialTransactionPosition, bctx: BlockEContext, ops: List<OpData>): Boolean {
            logger.info("ext validateSpecialOperations")
            return true
        }

        override fun disconnectProcess(process: BlockchainProcess) {
            logger.info("ext disconnectProcess")
            hasExtDisconnect = true
        }
    }
}
