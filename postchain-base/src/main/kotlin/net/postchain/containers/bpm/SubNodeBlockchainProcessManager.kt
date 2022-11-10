package net.postchain.containers.bpm

import net.postchain.PostchainContext
import net.postchain.base.BaseBlockchainProcessManager
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.block.BlockTrace
import net.postchain.containers.bpm.bcconfig.SubnodeBlockchainConfigurationConfig
import net.postchain.containers.bpm.bcconfig.DefaultSubnodeBlockchainConfigListener
import net.postchain.containers.bpm.bcconfig.SubnodeBlockchainConfigListener
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

    protected val subnodeBcCfgConfig = SubnodeBlockchainConfigurationConfig.fromAppConfig(appConfig)
    protected val subnodeBcCfgListeners: MutableMap<Long, SubnodeBlockchainConfigListener> = ConcurrentHashMap()

    override fun awaitPermissionToProcessMessages(blockchainConfig: BlockchainConfiguration): (Long, () -> Boolean) -> Boolean {
        return if (!subnodeBcCfgConfig.enabled) {
            { _, _ -> true }
        } else {
            val listener: SubnodeBlockchainConfigListener = DefaultSubnodeBlockchainConfigListener(
                    subnodeBcCfgConfig, blockchainConfig.chainID, blockchainConfig.blockchainRid, connectionManager as SubConnectionManager
            ).also {
                it.blockchainConfigProvider = blockchainConfigProvider
                it.storage = storage
                subnodeBcCfgListeners[blockchainConfig.chainID] = it
            }

            return configCheck@{ blockTimestamp, exitCondition ->
                while (!listener.checkConfig(blockTimestamp)) {
                    if (exitCondition()) {
                        return@configCheck false
                    }
                    Thread.sleep(subnodeBcCfgConfig.sleepTimeout)
                }
                true
            }
        }
    }

    override fun stopAndUnregisterBlockchainProcess(chainId: Long, restart: Boolean, bTrace: BlockTrace?) {
        subnodeBcCfgListeners.remove(chainId)
        super.stopAndUnregisterBlockchainProcess(chainId, restart, bTrace)
    }
}
