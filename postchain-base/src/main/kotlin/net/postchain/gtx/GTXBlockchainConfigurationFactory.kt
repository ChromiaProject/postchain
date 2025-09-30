package net.postchain.gtx

import mu.KLogging
import mu.withLoggingContext
import net.postchain.base.configuration.BlockchainConfigurationData
import net.postchain.base.configuration.BlockchainConfigurationOptions
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.snapshot.SnapshotBlockchainConfigurationData
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.core.BlockchainConfigurationFactory
import net.postchain.core.BlockchainContext
import net.postchain.core.EContext
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.gtv.Gtv
import net.postchain.gtv.mapper.toObject
import net.postchain.logging.BLOCKCHAIN_RID_TAG
import net.postchain.logging.CHAIN_IID_TAG

/**
 * TODO: (Olle) This should be in the "net.postchain.base.gtx" package (setting it apart from the GTX module),
 *       but that would mean many BC configurations get a new BC RID, messing up many tests :-(
 */
open class GTXBlockchainConfigurationFactory : BlockchainConfigurationFactory {

    companion object : KLogging() {
        fun validateConfiguration(config: Gtv, blockchainRid: BlockchainRid, eContext: EContext) {
            val configurationData = try {
                config.toObject<BlockchainConfigurationData>()
            } catch (e: IllegalArgumentException) {
                throw UserMistake("Unable to parse configuration: ${e.message}", e)
            }
            extraConfigurationValidation(configurationData, eContext)
            makeGtxModule(blockchainRid, configurationData)
        }

        fun extraConfigurationValidation(configurationData: BlockchainConfigurationData, eContext: EContext) {
            val dba = DatabaseAccess.of(eContext)
            val currentMerkleHashVersion = dba.getCurrentMerkleHashVersion(eContext)
            val newMerkleHashVersion = configurationData.merkleHashVersion
            if (newMerkleHashVersion < currentMerkleHashVersion) {
                throw UserMistake("Cannot downgrade merkle hash version from $currentMerkleHashVersion to $newMerkleHashVersion")
            }

            if (configurationData.snapshotsEnabled && !dba.isSnapshotEnabled(eContext)) {
                throw UserMistake("Snapshots can only be enabled on height 0")
            }
        }

        internal fun makeGtxModule(blockchainRID: BlockchainRid, data: BlockchainConfigurationData): GTXModule {
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

                val moduleClass = try {
                    Class.forName(className).getConstructor()
                } catch (_: ClassNotFoundException) {
                    throw UserMistake("GTX module class not found: $className")
                }
                try {
                    return when (val instance = moduleClass.newInstance()) {
                        is GTXModule -> instance
                        is GTXModuleFactory -> instance.makeModule(data.rawConfig, blockchainRID)
                        else -> throw UserMistake("GTX module class not recognized: $className. Expected GTXModule or GTXModuleFactory.")
                    }
                } catch (e: Exception) {
                    logger.warn("Unable to create GTX module: ${e.message}", e)
                    throw UserMistake("Unable to create GTX module: $className")
                }
            }

            val snapshotConfig = data.snapshot?.toObject<SnapshotBlockchainConfigurationData>()
                    ?: SnapshotBlockchainConfigurationData.default
            return CompositeGTXModule(list.map(::makeModule).toTypedArray(), gtxConfig.allowOverrides,
                    data.snapshotsEnabled, snapshotConfig.snapshotInterval, snapshotConfig.levelsPerPage)
        }
    }

    override fun makeBlockchainConfiguration(
            configurationData: Any,
            partialContext: BlockchainContext,
            blockSigMaker: SigMaker,
            eContext: EContext,
            cryptoSystem: CryptoSystem,
            blockchainConfigurationOptions: BlockchainConfigurationOptions
    ): GTXBlockchainConfiguration {
        val bcConfigData = configurationData as BlockchainConfigurationData
        val effectiveBRID = bcConfigData.historicBrid ?: partialContext.blockchainRID
        return GTXBlockchainConfiguration(
                bcConfigData,
                cryptoSystem,
                partialContext,
                blockSigMaker,
                createGtxModule(effectiveBRID, configurationData, eContext),
                blockchainConfigurationOptions
        )
    }

    open fun createGtxModule(blockchainRID: BlockchainRid, data: BlockchainConfigurationData, eContext: EContext): GTXModule = withLoggingContext(
            BLOCKCHAIN_RID_TAG to blockchainRID.toHex(), CHAIN_IID_TAG to eContext.chainID.toString()
    ) {
        makeGtxModule(blockchainRID, data).apply {
            GTXSchemaManager.initializeDB(eContext)
            initializeDB(eContext)
        }
    }
}
