package net.postchain.containers.bpm

import net.postchain.PostchainContext
import net.postchain.base.BaseBlockchainProcessManager
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainInfrastructure
import net.postchain.ebft.heartbeat.DefaultHeartbeatManager
import net.postchain.ebft.heartbeat.HeartbeatConfig
import net.postchain.ebft.heartbeat.HeartbeatListener
import net.postchain.ebft.heartbeat.RemoteConfigHeartbeatListener
import net.postchain.network.mastersub.subnode.SubConnectionManager
import java.lang.Thread.sleep

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
    protected val heartbeatManager = DefaultHeartbeatManager(heartbeatConfig)

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
            { timestamp, exitCondition ->
                var passed = true
                while (!hbListener.checkHeartbeat(timestamp)) {
                    if (exitCondition()) {
                        passed = false
                        break
                    }
                    sleep(heartbeatConfig.sleepTimeout)
                }
                passed
            }
        }
    }

    override fun stopAndUnregisterBlockchainProcess(chainId: Long, restart: Boolean) {
        heartbeatManager.removeListener(chainId)
        super.stopAndUnregisterBlockchainProcess(chainId, restart)
    }

}
