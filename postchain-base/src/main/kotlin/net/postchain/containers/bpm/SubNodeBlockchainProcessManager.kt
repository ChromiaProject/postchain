package net.postchain.containers.bpm

import net.postchain.base.BaseBlockchainProcessManager
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainInfrastructure
import net.postchain.debug.BlockchainProcessName
import net.postchain.debug.NodeDiagnosticContext
import net.postchain.ebft.heartbeat.DefaultHeartbeatManager
import net.postchain.ebft.heartbeat.HeartbeatListener
import net.postchain.ebft.heartbeat.RemoteConfigHeartbeatListener
import net.postchain.network.mastersub.subnode.DefaultSubConnectionManager

open class SubNodeBlockchainProcessManager(
        blockchainInfrastructure: BlockchainInfrastructure,
        nodeConfigProvider: NodeConfigurationProvider,
        blockchainConfigProvider: BlockchainConfigurationProvider,
        nodeDiagnosticContext: NodeDiagnosticContext
) : BaseBlockchainProcessManager(
        blockchainInfrastructure,
        nodeConfigProvider,
        blockchainConfigProvider,
        nodeDiagnosticContext
) {

    private val heartbeatManager = DefaultHeartbeatManager(nodeConfig)
    private val heartbeatListeners = mutableMapOf<Long, HeartbeatListener>()
    val connectionManager = DefaultSubConnectionManager(nodeConfig)

    override fun createAndRegisterBlockchainProcess(chainId: Long, blockchainConfig: BlockchainConfiguration, processName: BlockchainProcessName, engine: BlockchainEngine, heartbeatListener: HeartbeatListener?) {
        val hbListener = if (nodeConfig.remoteConfigEnabled) {
            RemoteConfigHeartbeatListener(nodeConfig, chainId, blockchainConfig.blockchainRid, connectionManager).also {
                it.blockchainConfigProvider = blockchainConfigProvider
                it.storage = storage
                heartbeatManager.addListener(it)
                heartbeatListeners[chainId] = it
            }
        } else {
            null
        }
        super.createAndRegisterBlockchainProcess(chainId, blockchainConfig, processName, engine, hbListener)
    }

    override fun stopAndUnregisterBlockchainProcess(chainId: Long, restart: Boolean) {
        super.stopAndUnregisterBlockchainProcess(chainId, restart)
        heartbeatListeners.remove(chainId)?.also {
            heartbeatManager.removeListener(it)
        }
    }


}
