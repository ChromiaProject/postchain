package net.postchain.gtx

import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.common.BlockchainRid
import net.postchain.common.data.Hash
import net.postchain.common.exception.UserMistake
import net.postchain.core.BlockchainConfigurationFactory
import net.postchain.core.BlockchainContext
import net.postchain.core.EContext
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.crypto.SigMaker
import net.postchain.crypto.Signature
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.mapper.toObject

/**
 * TODO: (Olle) This should be in the "net.postchain.base.gtx" package (setting it apart from the GTX module),
 *       but that would mean many BC configurations get a new BC RID, messing up many tests :-(
 */
open class GTXBlockchainConfigurationFactory : BlockchainConfigurationFactory {

    companion object {
        fun validateConfiguration(config: Gtv, blockchainRid: BlockchainRid) {
            val configurationData = try {
                BlockchainConfigurationData.fromRaw(
                        GtvEncoder.encodeGtv(config), blockchainRid, -1, -1L, ByteArray(0),
                        object : SigMaker {
                            override fun signMessage(msg: ByteArray): Signature = throw NotImplementedError()
                            override fun signDigest(digest: Hash): Signature = throw NotImplementedError()
                        })
            } catch (e: IllegalArgumentException) {
                throw UserMistake("Unable to parse configuration: ${e.message}", e)
            }
            makeGtxModule(blockchainRid, configurationData)
        }

        private fun makeGtxModule(blockchainRID: BlockchainRid, data: BlockchainConfigurationData): GTXModule {
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

    override fun makeBlockchainConfiguration(configurationData: Any,
                                             partialContext: BlockchainContext,
                                             blockSigMaker: SigMaker,
                                             eContext: EContext,
                                             cryptoSystem: CryptoSystem): GTXBlockchainConfiguration {
        val cfData = configurationData as BlockchainConfigurationData
        val effectiveBRID = cfData.historicBrid ?: partialContext.blockchainRID
        return GTXBlockchainConfiguration(
                cfData,
                cryptoSystem,
                partialContext,
                blockSigMaker,
                createGtxModule(effectiveBRID, configurationData, eContext)
        )
    }

        private fun makeGtxModule(blockchainRID: BlockchainRid, data: BlockchainConfigurationData): GTXModule {
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

    override fun makeBlockchainConfiguration(configurationData: Any, eContext: EContext, cryptoSystem: CryptoSystem): GTXBlockchainConfiguration {
        val cfData = configurationData as BlockchainConfigurationData
        val effectiveBRID = cfData.historicBrid ?: configurationData.context.blockchainRID
        return GTXBlockchainConfiguration(
                cfData,
                cryptoSystem,
                createGtxModule(effectiveBRID, configurationData, eContext)
        )
    }

    open fun createGtxModule(blockchainRID: BlockchainRid, data: BlockchainConfigurationData, eContext: EContext): GTXModule =
            makeGtxModule(blockchainRID, data).apply {
                GTXSchemaManager.initializeDB(eContext)
                initializeDB(eContext)
            }
}
