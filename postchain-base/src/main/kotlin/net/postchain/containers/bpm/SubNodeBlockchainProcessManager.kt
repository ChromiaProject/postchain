package net.postchain.containers.bpm

import net.postchain.PostchainContext
import net.postchain.base.BaseBlockchainProcessManager
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.block.BlockTrace
import net.postchain.ebft.heartbeat.*
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

    protected val heartbeatConfig = HeartbeatConfig.fromAppConfig(appConfig)
    protected val heartbeatManager = DefaultHeartbeatManager()

    override fun awaitPermissionToProcessMessages(blockchainConfig: BlockchainConfiguration):  (Long, () -> Boolean) -> Boolean {
        return if (!heartbeatConfig.remoteConfigEnabled) {
            { _, _ -> true }
        } else {
            val hbListener: HeartbeatListener = RemoteConfigHeartbeatListener(
                    heartbeatConfig, blockchainConfig.chainID, blockchainConfig.blockchainRid, connectionManager as SubConnectionManager
            ).also {
                it.blockchainConfigProvider = blockchainConfigProvider
                it.storage = storage
                heartbeatManager.addListener(blockchainConfig.chainID, it)
            };
            awaitHeartbeatHandler(hbListener, heartbeatConfig)
        }
    }

    override fun stopAndUnregisterBlockchainProcess(chainId: Long, restart: Boolean, bTrace: BlockTrace?) {
        heartbeatManager.removeListener(chainId)
        super.stopAndUnregisterBlockchainProcess(chainId, restart, bTrace)
    }

}
