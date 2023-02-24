package net.postchain.gtx

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.core.BlockchainConfigurationFactory
import net.postchain.core.BlockchainContext
import net.postchain.core.EContext
import net.postchain.core.block.BlockQueriesProvider
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.gtv.Gtv
import net.postchain.gtv.mapper.toObject
import net.postchain.network.mastersub.MasterSubQueryManager

/**
 * TODO: (Olle) This should be in the "net.postchain.base.gtx" package (setting it apart from the GTX module),
 *       but that would mean many BC configurations get a new BC RID, messing up many tests :-(
 */
open class GTXBlockchainConfigurationFactory : BlockchainConfigurationFactory {

    companion object {
        fun validateConfiguration(
                config: Gtv,
                blockchainRid: BlockchainRid
        ) {
            val configurationData = try {
                config.toObject<BlockchainConfigurationData>()
            } catch (e: IllegalArgumentException) {
                throw UserMistake("Unable to parse configuration: ${e.message}", e)
            }
            makeGtxModule(blockchainRid, configurationData, null, null)
        }

        private fun makeGtxModule(
                blockchainRID: BlockchainRid,
                data: BlockchainConfigurationData,
                blockQueriesProvider: BlockQueriesProvider?,
                masterSubQueryManager: MasterSubQueryManager?
        ): GTXModule {
            val gtxConfig = data.gtx?.toObject() ?: GtxConfigurationData.default
            val list = gtxConfig.modules.distinct()
            if (list.isEmpty()) {
                throw UserMistake("Missing GTX module in config. expected property 'blockchain.<chainId>.gtx.modules'")
            }

            fun makeModule(name: String): GTXModule {
                // Alias function for GTX modules that have been moved
                val className = when (name) {
                    "PatchOpsGTXModule" -> PatchOpsGTXModule::class.qualifiedName
                    else -> name
                }

                val moduleClass = Class.forName(className).getConstructor()
                return when (val instance = moduleClass.newInstance()) {
                    is GTXModule -> instance
                    is GTXModuleFactory -> instance.makeModule(data.rawConfig, blockchainRID)
                    is BlockQueriesGTXModuleFactory -> instance.makeModule(blockQueriesProvider, masterSubQueryManager)
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

    override fun makeBlockchainConfiguration(
            configurationData: Any,
            partialContext: BlockchainContext,
            blockSigMaker: SigMaker,
            eContext: EContext,
            cryptoSystem: CryptoSystem,
            blockQueriesProvider: BlockQueriesProvider?,
            masterSubQueryManager: MasterSubQueryManager?
    ): GTXBlockchainConfiguration {
        val cfData = configurationData as BlockchainConfigurationData
        val effectiveBRID = cfData.historicBrid ?: partialContext.blockchainRID
        return GTXBlockchainConfiguration(
                cfData,
                cryptoSystem,
                partialContext,
                blockSigMaker,
                createGtxModule(effectiveBRID, configurationData, eContext, blockQueriesProvider, masterSubQueryManager)
        )
    }

    open fun createGtxModule(
            blockchainRID: BlockchainRid,
            data: BlockchainConfigurationData,
            eContext: EContext,
            blockQueriesProvider: BlockQueriesProvider?,
            masterSubQueryManager: MasterSubQueryManager?
    ): GTXModule =
            makeGtxModule(blockchainRID, data, blockQueriesProvider, masterSubQueryManager).apply {
                GTXSchemaManager.initializeDB(eContext)
                initializeDB(eContext)
            }
}
