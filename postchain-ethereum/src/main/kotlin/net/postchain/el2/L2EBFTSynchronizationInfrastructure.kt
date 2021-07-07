package net.postchain.el2

import net.postchain.base.*

import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainProcess
import net.postchain.core.SynchronizationInfrastructureExtension

import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.DpNodeType
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ebft.*
import net.postchain.gtx.GTXBlockchainConfiguration
import java.util.*


data class EVML2Config (val url: String)

fun BaseBlockchainConfigurationData.getEL2Data() {
    this.data["evm_l2"]
}


class EL2SynchronizationInfrastructureExtension(
    nodeConfigProvider: NodeConfigurationProvider,
    nodeDiagnosticContext: NodeDiagnosticContext
) : SynchronizationInfrastructureExtension {

    override fun connectProcess(process: BlockchainProcess) {
        val engine = process.getEngine()
        val cfg = engine.getConfiguration()
        if (cfg is GTXBlockchainConfiguration) {
//            val ext = cfg.module.getSpecialTxExtensions().find { it is EL2SpecialTxExtension }
            val exs = cfg.module.getSpecialTxExtensions()
            var el2Ext: EL2SpecialTxExtension? = null
            for (ext in exs) {
                if (ext is EL2SpecialTxExtension) {
                    el2Ext = ext
                    break
                }
            }
            if (el2Ext != null) {
//                val blockchainConfig =
//                    engine.getConfiguration() as GTXBlockchainConfiguration // TODO: [et]: Resolve type cast
//                val layer2 = blockchainConfig.configData.getLayer2()
//                val unregisterBlockchainDiagnosticData: () -> Unit = {
//                    blockchainProcessesDiagnosticData.remove(blockchainConfig.blockchainRid)
//                }
//                val peerCommConfiguration = buildPeerCommConfiguration(nodeConfig, blockchainConfig)

//                URL should actually come from node config

//                val url = layer2?.get("eth_rpc_api_node_url")?.asString() ?: "http://localhost:8545"
//
//                while address is in bc config. also bc config should include network name, so
//                we can support BSC etc.
//
//                val contractAddress = layer2?.get("contract_address")?.asString() ?: "0x0"
                val eventProcessor = EthereumEventProcessor(
                    "https://rinkeby.infura.io/v3/dd30c7e83e574688a429a72d2965c8e5",
                    "0xcaF05a52A7bf133A5dbEb1F12efb1B7375153455",
                    engine.getBlockQueries()
                )
                el2Ext.useEventProcessor(eventProcessor)
            }
        }
    }

    override fun shutdown() {}
}