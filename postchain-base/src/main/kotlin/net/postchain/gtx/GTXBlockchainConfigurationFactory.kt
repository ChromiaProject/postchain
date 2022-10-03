package net.postchain.gtx

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.base.data.DependenciesValidator
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainConfigurationFactory
import net.postchain.core.EContext
import net.postchain.gtv.mapper.toObject

/**
 * TODO: [Olle] This should be in the "net.postchain.base.gtx" pagkage (setting it apart from the GTX module),
 *       but that would mean many BC configurations get a new BC RID, messing up many tests :-(
 */
open class GTXBlockchainConfigurationFactory : BlockchainConfigurationFactory {

    override fun makeBlockchainConfiguration(configurationData: Any, eContext: EContext): BlockchainConfiguration {
        val cfData = configurationData as BlockchainConfigurationData
        val effectiveBRID = cfData.historicBrid ?: configurationData.context.blockchainRID
        DependenciesValidator.validateBlockchainRids(eContext, configurationData.blockchainDependencies)
        return GTXBlockchainConfiguration(
            cfData,
            createGtxModule(effectiveBRID, configurationData, eContext)
        )
    }

    open fun createGtxModule(blockchainRID: BlockchainRid, data: BlockchainConfigurationData, eContext: EContext): GTXModule {

        val gtxConfig = data.gtx?.toObject() ?: GtxConfigurationData.default
        val list = gtxConfig.modules
        if (list.isEmpty()) {
            throw UserMistake("Missing GTX module in config. expected property 'blockchain.<chainId>.gtx.modules'")
        }

        fun makeModule(name: String): GTXModule {
            val moduleClass = Class.forName(name).getConstructor()
            return when (val instance = moduleClass.newInstance()) {
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
        }.apply {
            GTXSchemaManager.initializeDB(eContext)
            initializeDB(eContext)
        }
    }
}