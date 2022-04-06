package net.postchain.containers.bpm

import net.postchain.PostchainContext
import net.postchain.base.BaseBlockchainProcessManager
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainInfrastructure
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

    protected val heartbeatManager = DefaultHeartbeatManager(nodeConfig)

    override fun shouldProcessNewMessages(blockchainConfig: BlockchainConfiguration): (Long) -> Boolean {
        return if (!nodeConfig.remoteConfigEnabled) {
            { true }
        } else {
            val hbListener: HeartbeatListener = RemoteConfigHeartbeatListener(
                    nodeConfig, blockchainConfig.chainID, blockchainConfig.blockchainRid, connectionManager as SubConnectionManager
            ).also {
                it.blockchainConfigProvider = blockchainConfigProvider
                it.storage = storage
                heartbeatManager.addListener(blockchainConfig.chainID, it)
            };
            { hbListener.checkHeartbeat(it) }
        }
    }

    override fun stopAndUnregisterBlockchainProcess(chainId: Long, restart: Boolean) {
        heartbeatManager.removeListener(chainId)
        super.stopAndUnregisterBlockchainProcess(chainId, restart)
    }

}
