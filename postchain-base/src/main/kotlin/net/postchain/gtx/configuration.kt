// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.gtx

import mu.KLogging
import net.postchain.base.*
import net.postchain.base.configuration.BaseBlockchainConfiguration
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.core.*
import net.postchain.common.BlockchainRid
import net.postchain.gtv.Gtv
import net.postchain.gtv.gtvToJSON
import net.postchain.gtv.make_gtv_gson
import net.postchain.gtv.mapper.toObject
import nl.komponents.kovenant.Promise

open class GTXBlockchainConfiguration(configData: BlockchainConfigurationData,
                                      val module: GTXModule)
    : BaseBlockchainConfiguration(configData) {

    private val gtxConfig = configData.gtx?.toObject() ?: GtxConfigurationData.default

    private val txFactory = GTXTransactionFactory(
        effectiveBlockchainRID, module, cryptoSystem, gtxConfig.maxTxSize
    )
    private lateinit var specTxHandler: GTXSpecialTxHandler // Note: this is NOT the same as the variable in Base.

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

    override fun initializeDB(ctx: EContext) {
        super.initializeDB(ctx)
        logger.debug("Running initialize DB of class GTXBlockchainConfiguration using ctx chainIid: ${ctx.chainID}, BC RID: ${effectiveBlockchainRID.toShortHex()}")
        GTXSchemaManager.initializeDB(ctx)
        module.initializeDB(ctx)
        specTxHandler = GTXSpecialTxHandler(module, effectiveBlockchainRID, cryptoSystem, txFactory)
    }

    override fun makeBlockQueries(storage: Storage): BlockQueries {
        return object : BaseBlockQueries(this@GTXBlockchainConfiguration, storage, blockStore,
                chainID, configData.context.nodeRID!!) {
            private val gson = make_gtv_gson()

            override fun query(query: String): Promise<String, Exception> {
                val gtxQuery = gson.fromJson<Gtv>(query, Gtv::class.java)
                return runOp {
                    val type = gtxQuery.asDict()["type"] ?: throw UserMistake("Missing query type")
                    val queryResult = module.query(it, type.asString(), gtxQuery)
                    gtvToJSON(queryResult, gson)
                }
            }

            override fun query(name: String, args: Gtv): Promise<Gtv, Exception> {
                return runOp {
                    module.query(it, name, args)
                }
            }

        }
    }
}

open class GTXBlockchainConfigurationFactory : BlockchainConfigurationFactory {

    override fun makeBlockchainConfiguration(configurationData: Any): BlockchainConfiguration {
        val cfData = configurationData as BlockchainConfigurationData
        val effectiveBRID = cfData.historicBrid ?: configurationData.context.blockchainRID
        return GTXBlockchainConfiguration(
                cfData,
                createGtxModule(effectiveBRID, configurationData)
        )
    }

    open fun createGtxModule(blockchainRID: BlockchainRid, data: BlockchainConfigurationData): GTXModule {

        val gtxConfig = data.gtx?.toObject() ?: GtxConfigurationData.default
        val list = gtxConfig.modules
        if (list.isEmpty()) {
            throw UserMistake("Missing GTX module in config. expected property 'blockchain.<chainId>.gtx.modules'")
        }

        fun makeModule(name: String): GTXModule {
            val moduleClass = Class.forName(name)
            val instance = moduleClass.newInstance()
            return when (instance) {
                is GTXModule -> instance
                is GTXModuleFactory -> instance.makeModule(data.rawConfig, blockchainRID) //TODO
                else -> throw UserMistake("Module class not recognized")
            }
        }

        return if (list.size == 1) {
            makeModule(list[0])
        } else {
            val moduleList = list.map(::makeModule)
            CompositeGTXModule(moduleList.toTypedArray(), gtxConfig.allowOverrides)
        }
    }
}