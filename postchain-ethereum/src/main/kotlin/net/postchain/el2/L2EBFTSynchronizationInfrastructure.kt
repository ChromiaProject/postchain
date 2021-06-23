package net.postchain.el2

import net.postchain.base.*

import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainProcess

import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.DpNodeType
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ebft.*
import net.postchain.gtx.GTXBlockchainConfiguration


data class EVML2Config (val url: String)

fun BaseBlockchainConfigurationData.getEL2Data() {
    this.data["evm_l2"]
}


class L2EBFTSynchronizationInfrastructure(
    nodeConfigProvider: NodeConfigurationProvider,
    nodeDiagnosticContext: NodeDiagnosticContext
) : EBFTSynchronizationInfrastructure(nodeConfigProvider, nodeDiagnosticContext) {

    override fun makeBlockchainProcess(
        processName: BlockchainProcessName,
        engine: BlockchainEngine,
        historicBlockchainContext: HistoricBlockchainContext?
    ): BlockchainProcess {
        val process = super.makeBlockchainProcess(processName, engine, historicBlockchainContext)
        val cfg = engine.getConfiguration()
        if (cfg is GTXBlockchainConfiguration) {
            val ext = cfg.module.getSpecialTxExtensions().find { it is EL2SpecialTxExtension }
            if (ext != null && ext is EL2SpecialTxExtension) {
                /*
                val blockchainConfig = engine.getConfiguration() as GTXBlockchainConfiguration // TODO: [et]: Resolve type cast
                val layer2 = blockchainConfig.configData.getLayer2()
                val unregisterBlockchainDiagnosticData: () -> Unit = {
                    blockchainProcessesDiagnosticData.remove(blockchainConfig.blockchainRid)
                }
                val peerCommConfiguration = buildPeerCommConfiguration(nodeConfig, blockchainConfig)

                URL should actually come from node config

                val url = layer2?.get("eth_rpc_api_node_url")?.asString() ?: "http://localhost:8545"

                while address is in bc config. also bc config should include network name, so
                we can support BSC etc.

                val contractAddress = layer2?.get("contract_address")?.asString() ?: "0x0"
                val eventProcessor = EthereumEventProcessor(url, contractAddress, engine.getBlockQueries())
                (blockchainConfig.getSpecialTxHandler() as L2SpecialTxHandler).useEventProcessor(eventProcessor)

                 */
            }
        }

        return process
    }
}