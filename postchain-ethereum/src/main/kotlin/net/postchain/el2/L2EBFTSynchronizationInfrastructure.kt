package net.postchain.el2

import mu.KLogging
import net.postchain.base.BaseBlockchainConfigurationData
import net.postchain.config.node.NodeConfig
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainProcess
import net.postchain.core.SynchronizationInfrastructureExtension
import net.postchain.core.UserMistake
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ethereum.contracts.ChrL2
import net.postchain.gtx.GTXBlockchainConfiguration
import org.web3j.protocol.Web3j
import org.web3j.tx.ClientTransactionManager
import org.web3j.tx.gas.DefaultGasProvider
import java.math.BigInteger

data class EIFConfig(val contract: String, val contractDeployBlock: BigInteger)

fun BaseBlockchainConfigurationData.getEL2Data(): EIFConfig {
    val evmL2 = this.data["evm_eif"] ?: throw UserMistake("No EIF config present")
    val eth = evmL2["eth"] ?: throw UserMistake("No ethereum config present")
    val contract = eth["contract"]?.asString() ?: throw UserMistake("No ethereum contract address config present")
    val contractDeployBlock =
        eth["contractDeployBlock"]?.asBigInteger() ?: BigInteger.ZERO // Fallback to read from beginning
    return EIFConfig(contract, contractDeployBlock)
}

fun NodeConfig.getEthereumUrl(): String {
    return appConfig.config.getString("ethereum.url", "")
}

class EL2SynchronizationInfrastructureExtension(
    private val nodeConfigProvider: NodeConfigurationProvider,
    nodeDiagnosticContext: NodeDiagnosticContext
) : SynchronizationInfrastructureExtension {
    private lateinit var web3c: Web3Connector
    private lateinit var eventProcessor: EventProcessor

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
                val el2Config = cfg.configData.getEL2Data()
                if (el2Config.contractDeployBlock == BigInteger.ZERO) {
                    logger.warn("Contract deploy block config is set to 0. Consider changing it to real height to avoid redundant queries.")
                }

                initializeEventProcessor(el2Config, engine)
                el2Ext.useEventProcessor(eventProcessor)
            }
        }
    }

    private fun initializeEventProcessor(el2Config: EIFConfig, engine: BlockchainEngine) {
        val ethereumUrl = nodeConfigProvider.getConfiguration().getEthereumUrl()
        if ("ignore".equals(ethereumUrl, ignoreCase = true)) {
            logger.warn("EIF is running in disconnected mode. No events will be validated against ethereum.")
            eventProcessor = NoOpEventProcessor()
        } else {
            val web3j = Web3j.build(Web3jServiceFactory.buildService(ethereumUrl))
            web3c = Web3Connector(web3j, el2Config.contract)

            val contract = ChrL2.load(
                web3c.contractAddress,
                web3c.web3j,
                ClientTransactionManager(web3c.web3j, "0x0"),
                DefaultGasProvider()
            )

            eventProcessor = EthereumEventProcessor(
                web3c,
                contract,
                BigInteger.valueOf(100),
                el2Config.contractDeployBlock,
                engine
            ).apply { start() }
        }
    }

    override fun shutdown() {
        if (::eventProcessor.isInitialized) {
            eventProcessor.shutdown()
        }
        if (::web3c.isInitialized) {
            web3c.shutdown()
        }
    }
}