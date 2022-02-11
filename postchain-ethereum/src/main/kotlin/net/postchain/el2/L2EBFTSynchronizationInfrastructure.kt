package net.postchain.el2

import net.postchain.base.BaseBlockchainConfigurationData
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.BlockchainProcess
import net.postchain.core.SynchronizationInfrastructureExtension
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ethereum.contracts.ChrL2
import net.postchain.gtx.GTXBlockchainConfiguration
import org.web3j.protocol.Web3j
import org.web3j.tx.ClientTransactionManager
import org.web3j.tx.gas.DefaultGasProvider
import java.math.BigInteger

data class EVML2Config(val url: String, val contract: String)

fun BaseBlockchainConfigurationData.getEL2Data(): EVML2Config {
    val evmL2 = this.data["evm_l2"]
    val eth = evmL2!!["eth"]
    val url = eth!!["url"]!!.asString()
    val contract = eth["contract"]!!.asString()
    return EVML2Config(url, contract)
}

class EL2SynchronizationInfrastructureExtension(
    nodeConfigProvider: NodeConfigurationProvider,
    nodeDiagnosticContext: NodeDiagnosticContext
) : SynchronizationInfrastructureExtension {
    private lateinit var web3c: Web3Connector
    private lateinit var eventProcessor: EthereumEventProcessor

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
                val web3j = Web3j.build(Web3jServiceFactory.buildService(el2Config.url))
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
                    engine
                )
                eventProcessor.start()
                el2Ext.useEventProcessor(eventProcessor)
            }
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