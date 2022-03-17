package net.postchain.containers.bpm

import net.postchain.PostchainContext
import net.postchain.base.BaseBlockchainProcessManager
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainInfrastructure
import net.postchain.debug.BlockchainProcessName
import net.postchain.ebft.heartbeat.DefaultHeartbeatManager
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

    private val heartbeatManager = DefaultHeartbeatManager(nodeConfig)
    private val heartbeatListeners = mutableMapOf<Long, HeartbeatListener>()

    override fun createAndRegisterBlockchainProcess(chainId: Long, blockchainConfig: BlockchainConfiguration, processName: BlockchainProcessName, engine: BlockchainEngine, shouldProcessNewMessages: (Long) -> Boolean) {
        val hbListener = if (nodeConfig.remoteConfigEnabled) {
            RemoteConfigHeartbeatListener(nodeConfig, chainId, blockchainConfig.blockchainRid, connectionManager as SubConnectionManager).also {
                it.blockchainConfigProvider = blockchainConfigProvider
                it.storage = storage
                heartbeatManager.addListener(it)
                heartbeatListeners[chainId] = it
            }
        } else {
            null
        }
        super.createAndRegisterBlockchainProcess(chainId, blockchainConfig, processName, engine) { hbListener?.checkHeartbeat(it) ?: true }
    }

    override fun stopAndUnregisterBlockchainProcess(chainId: Long, restart: Boolean) {
        super.stopAndUnregisterBlockchainProcess(chainId, restart)
        heartbeatListeners.remove(chainId)?.also {
            heartbeatManager.removeListener(it)
        }
    }


}
