package net.postchain.containers.bpm

import net.postchain.PostchainContext
import net.postchain.base.BaseBlockchainProcessManager
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.block.BlockTrace
import net.postchain.ebft.remoteconfig.RemoteConfigConfig
import net.postchain.ebft.remoteconfig.DefaultRemoteConfigListener
import net.postchain.ebft.remoteconfig.RemoteConfigListener
import net.postchain.network.mastersub.subnode.SubConnectionManager
import java.util.concurrent.ConcurrentHashMap

open class SubNodeBlockchainProcessManager(
        postchainContext: PostchainContext,
        blockchainInfrastructure: BlockchainInfrastructure,
        blockchainConfigProvider: BlockchainConfigurationProvider
) : BaseBlockchainProcessManager(
        postchainContext,
        blockchainInfrastructure,
        blockchainConfigProvider
) {

    protected val remoteConfigConfig = RemoteConfigConfig.fromAppConfig(appConfig)
    protected val remoteConfigListeners: MutableMap<Long, RemoteConfigListener> = ConcurrentHashMap()

    override fun awaitPermissionToProcessMessages(blockchainConfig: BlockchainConfiguration): (Long, () -> Boolean) -> Boolean {
        return if (!remoteConfigConfig.enabled) {
            { _, _ -> true }
        } else {
            val listener: RemoteConfigListener = DefaultRemoteConfigListener(
                    remoteConfigConfig, blockchainConfig.chainID, blockchainConfig.blockchainRid, connectionManager as SubConnectionManager
            ).also {
                it.blockchainConfigProvider = blockchainConfigProvider
                it.storage = storage
                remoteConfigListeners[blockchainConfig.chainID] = it
            }

            return hbCheck@{ blockTimestamp, exitCondition ->
                while (!listener.checkRemoteConfig(blockTimestamp)) {
                    if (exitCondition()) {
                        return@hbCheck false
                    }
                    Thread.sleep(remoteConfigConfig.sleepTimeout)
                }
                true
            }
        }
    }

    override fun stopAndUnregisterBlockchainProcess(chainId: Long, restart: Boolean, bTrace: BlockTrace?) {
        remoteConfigListeners.remove(chainId)
        super.stopAndUnregisterBlockchainProcess(chainId, restart, bTrace)
    }
}
