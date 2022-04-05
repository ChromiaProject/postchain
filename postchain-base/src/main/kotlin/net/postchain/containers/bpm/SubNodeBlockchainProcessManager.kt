package net.postchain.containers.bpm

import net.postchain.PostchainContext
import net.postchain.base.BaseBlockchainProcessManager
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainInfrastructure
import net.postchain.debug.BlockchainProcessName
import net.postchain.ebft.heartbeat.HeartbeatListener
import net.postchain.ebft.heartbeat.RemoteConfigHeartbeatListener
import net.postchain.network.mastersub.subnode.SubConnectionManager

open class SubNodeBlockchainProcessManager(
        postchainContext: PostchainContext,
        blockchainInfrastructure: BlockchainInfrastructure,
        blockchainConfigProvider: BlockchainConfigurationProvider
) : BaseBlockchainProcessManager(
        postchainContext,
        blockchainInfrastructure,
        blockchainConfigProvider
) {

    override fun createAndRegisterBlockchainProcess(
            chainId: Long,
            blockchainConfig: BlockchainConfiguration,
            processName: BlockchainProcessName,
            engine: BlockchainEngine,
            shouldProcessNewMessages: (Long) -> Boolean
    ) {
        val hbListener: HeartbeatListener = RemoteConfigHeartbeatListener(
                nodeConfig, chainId, blockchainConfig.blockchainRid, connectionManager as SubConnectionManager
        ).also {
            it.blockchainConfigProvider = blockchainConfigProvider
            it.storage = storage
            heartbeatManager.addListener(chainId, it)
        }

        blockchainProcesses[chainId] = blockchainInfrastructure.makeBlockchainProcess(processName, engine) {
            hbListener.checkHeartbeat(it)
        }.also {
            it.registerDiagnosticData(blockchainProcessesDiagnosticData.getOrPut(blockchainConfig.blockchainRid) { mutableMapOf() })
            extensions.forEach { ext -> ext.connectProcess(it) }
            chainIdToBrid[chainId] = blockchainConfig.blockchainRid
        }
    }

}
