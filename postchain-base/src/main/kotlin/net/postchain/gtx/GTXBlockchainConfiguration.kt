// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.base.SpecialTransactionHandler
import net.postchain.base.configuration.BaseBlockchainConfiguration
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.core.BlockchainContext
import net.postchain.core.Storage
import net.postchain.core.TransactionFactory
import net.postchain.core.block.BlockQueries
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.gtv.mapper.toObject
import net.postchain.gtx.special.GTXSpecialTxHandler

open class GTXBlockchainConfiguration(configData: BlockchainConfigurationData,
                                      cryptoSystem: CryptoSystem,
                                      partialContext: BlockchainContext,
                                      blockSigMaker: SigMaker,
                                      final override val module: GTXModule
) : BaseBlockchainConfiguration(configData, cryptoSystem, partialContext, blockSigMaker), GTXModuleAware {

    private val gtxConfig = configData.gtx?.toObject() ?: GtxConfigurationData.default

    private val txFactory = GTXTransactionFactory(
            effectiveBlockchainRID, module, cryptoSystem, gtxConfig.maxTxSize
    )

    private val specTxHandler: GTXSpecialTxHandler // Note: this is NOT the same as the variable in Base.
            = GTXSpecialTxHandler(
            module,
            this.chainID,
            effectiveBlockchainRID,
            cryptoSystem,
            txFactory)


    companion object : KLogging()

    override fun makeBBExtensions(): List<BaseBlockBuilderExtension> {
        return module.makeBlockBuilderExtensions()
    }

    override fun getTransactionFactory(): TransactionFactory {
        return txFactory
    }

    override fun getSpecialTxHandler(): SpecialTransactionHandler {
        return specTxHandler // NOTE: not the same as "specialTransactionHandler" in Base
    }

    override fun makeBlockQueries(storage: Storage): BlockQueries =
            GTXBlockQueries(this@GTXBlockchainConfiguration, storage, blockStore, chainID, blockchainContext.nodeRID!!, module)

    override fun hasQuery(name: String): Boolean = module.getQueries().contains(name)

    override fun initializeModules(postchainContext: PostchainContext) {
        if (module is PostchainContextAware) {
            module.initializeContext(this, postchainContext)
        }
        super.initializeModules(postchainContext)
    }

    override fun shutdownModules() {
        module.shutdown()
        super.shutdownModules()
    }
}
