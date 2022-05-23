package net.postchain.el2

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.core.*
import net.postchain.el2.config.EifBlockchainConfig
import net.postchain.el2.config.EifConfig
import net.postchain.gtv.mapper.toObject
import net.postchain.gtx.GTXBlockchainConfiguration
import org.web3j.protocol.Web3j
import java.math.BigInteger

class EL2SynchronizationInfrastructureExtension(
    private val postchainContext: PostchainContext
) : SynchronizationInfrastructureExtension {
    private val eventProcessors = mutableMapOf<String, EventProcessor>()

    companion object : KLogging()

    override fun connectProcess(process: BlockchainProcess) {
        val engine = process.blockchainEngine
        val cfg = engine.getConfiguration()
        if (cfg is GTXBlockchainConfiguration) {
            val exs = cfg.module.getSpecialTxExtensions()
            val el2Ext = exs.find { it is EL2SpecialTxExtension }
            if (el2Ext is EL2SpecialTxExtension) {
                val eifBlockchainConfig = cfg.configData.rawConfig["eif"]?.toObject<EifBlockchainConfig>()
                        ?: throw UserMistake("No EIF config present")
                if (eifBlockchainConfig.skipToHeight == BigInteger.ZERO) {
                    logger.warn("Skip to height config is set to 0. Consider changing it to avoid redundant queries.")
                }

                val eifConfig = EifConfig.fromAppConfig(postchainContext.appConfig)
                val eventProcessor = initializeEventProcessor(eifBlockchainConfig, engine, eifConfig)
                el2Ext.useEventProcessor(eventProcessor)
                eventProcessors[cfg.blockchainRid.toHex()] = eventProcessor
            }
        }
    }

    override fun disconnectProcess(process: BlockchainProcess) {
        val blockchainRid = process.blockchainEngine.getConfiguration().blockchainRid.toHex()
        val eventProcessor = eventProcessors.remove(blockchainRid)
            ?: throw ProgrammerMistake("Blockchain $blockchainRid not attached")
        eventProcessor.shutdown()
    }

    override fun shutdown() {}

    private fun initializeEventProcessor(eifBlockchainConfig: EifBlockchainConfig, engine: BlockchainEngine, eifConfig: EifConfig): EventProcessor {
        return if ("ignore".equals(eifConfig.url, ignoreCase = true)) {
            logger.warn("EIF is running in disconnected mode. No events will be validated against ethereum.")
            NoOpEventProcessor()
        } else {
            val web3j = Web3j.build(Web3jServiceFactory.buildService(eifConfig))

            EthereumEventProcessor(
                web3j,
                eifBlockchainConfig.contracts,
                BigInteger.valueOf(eifBlockchainConfig.readOffset),
                eifBlockchainConfig.skipToHeight,
                engine
            ).apply { start() }
        }
    }
}