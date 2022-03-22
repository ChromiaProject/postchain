package net.postchain.el2

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.base.BaseBlockchainConfigurationData
import net.postchain.config.node.NodeConfig
import net.postchain.core.*
import net.postchain.gtx.GTXBlockchainConfiguration
import org.web3j.protocol.Web3j
import java.math.BigInteger

data class EIFConfig(val contract: String, val contractDeployBlock: BigInteger)

fun BaseBlockchainConfigurationData.getEifConfigData(): EIFConfig {
    val evmEif = this.data["evm_eif"] ?: throw UserMistake("No EIF config present")
    val eth = evmEif["eth"] ?: throw UserMistake("No ethereum config present")
    val contract = eth["contract"]?.asString() ?: throw UserMistake("No ethereum contract address config present")
    val contractDeployBlock =
        eth["contractDeployBlock"]?.asBigInteger() ?: BigInteger.ZERO // Fallback to read from beginning
    return EIFConfig(contract, contractDeployBlock)
}

fun NodeConfig.getEthereumUrl(): String {
    return appConfig.config.getString("ethereum.url", "")
}

class EL2SynchronizationInfrastructureExtension(
    private val postchainContext: PostchainContext
) : SynchronizationInfrastructureExtension {
    private var eventProcessors = mutableMapOf<String, EventProcessor>()

    companion object : KLogging()

    override fun connectProcess(process: BlockchainProcess) {
        val engine = process.blockchainEngine
        val cfg = engine.getConfiguration()
        if (cfg is GTXBlockchainConfiguration) {
            val exs = cfg.module.getSpecialTxExtensions()
            var el2Ext: EL2SpecialTxExtension? = null
            for (ext in exs) {
                if (ext is EL2SpecialTxExtension) {
                    el2Ext = ext
                    break
                }
            }
            if (el2Ext != null) {
                val eifConfig = cfg.configData.getEifConfigData()
                if (eifConfig.contractDeployBlock == BigInteger.ZERO) {
                    logger.warn("Contract deploy block config is set to 0. Consider changing it to real height to avoid redundant queries.")
                }

                val eventProcessor = initializeEventProcessor(eifConfig, engine)
                el2Ext.useEventProcessor(eventProcessor)
                eventProcessors[cfg.blockchainRid.toHex()] = eventProcessor
            }
        }
    }

    override fun disconnectProcess(process: BlockchainProcess) {
        val blockchainRid = process.blockchainEngine.getConfiguration().blockchainRid.toHex()
        val eventProcessor = eventProcessors.remove(blockchainRid)

        if (eventProcessor != null) {
            eventProcessor.shutdown()
        } else {
            throw ProgrammerMistake("Blockchain $blockchainRid not attached")
        }
    }

    override fun shutdown() {}

    private fun initializeEventProcessor(eifConfig: EIFConfig, engine: BlockchainEngine): EventProcessor {
        val ethereumUrl = postchainContext.nodeConfigProvider.getConfiguration().getEthereumUrl()
        if ("ignore".equals(ethereumUrl, ignoreCase = true)) {
            logger.warn("EIF is running in disconnected mode. No events will be validated against ethereum.")
            return NoOpEventProcessor()
        } else {
            val web3j = Web3j.build(Web3jServiceFactory.buildService(ethereumUrl))
            val web3c = Web3Connector(web3j, eifConfig.contract)

            return EthereumEventProcessor(
                web3c,
                eifConfig.contract,
                BigInteger.valueOf(100),
                eifConfig.contractDeployBlock,
                engine
            ).apply { start() }
        }
    }
}