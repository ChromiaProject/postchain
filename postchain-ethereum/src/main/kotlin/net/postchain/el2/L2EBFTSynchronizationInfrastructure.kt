package net.postchain.el2

import net.postchain.base.BaseBlockchainConfigurationData
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.BlockchainProcess
import net.postchain.core.SynchronizationInfrastructureExtension
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.gtx.GTXBlockchainConfiguration

data class EVML2Config (val url: String, val contract: String)

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
                val eventProcessor = EthereumEventProcessor(
                    el2Config.url,
                    el2Config.contract,
                    engine.getBlockQueries()
                )
                el2Ext.useEventProcessor(eventProcessor)
            }
        }
    }

    override fun shutdown() {}
}