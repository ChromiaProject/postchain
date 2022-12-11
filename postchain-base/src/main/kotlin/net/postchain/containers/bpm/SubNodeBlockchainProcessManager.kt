package net.postchain.containers.bpm

import net.postchain.PostchainContext
import net.postchain.base.BaseBlockchainProcessManager
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.containers.bpm.bcconfig.BlockWiseSubnodeBlockchainConfigListener
import net.postchain.containers.bpm.bcconfig.SubnodeBlockchainConfigListener
import net.postchain.containers.bpm.bcconfig.SubnodeBlockchainConfigurationConfig
import net.postchain.core.AfterCommitHandler
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.block.BlockTrace
import net.postchain.network.mastersub.protocol.MsCommittedBlockMessage
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

    override fun awaitPermissionToProcessMessages(blockchainConfig: BlockchainConfiguration): (() -> Boolean) -> Boolean {
        return if (!subnodeBcCfgConfig.enabled) {
            { _ -> true }
        } else {
            /*
            val listener: SubnodeBlockchainConfigListener = DefaultSubnodeBlockchainConfigListener(
                    appConfig, subnodeBcCfgConfig, blockchainConfig.chainID, blockchainConfig.blockchainRid, connectionManager as SubConnectionManager
            ).
             */
            val listener: SubnodeBlockchainConfigListener = BlockWiseSubnodeBlockchainConfigListener(
                    appConfig, blockchainConfig.chainID, blockchainConfig.blockchainRid, connectionManager as SubConnectionManager
            ).also {
                it.blockchainConfigProvider = blockchainConfigProvider
                it.storage = storage
                subnodeBcCfgListeners[blockchainConfig.chainID] = it
            }

            return configCheck@{ exitCondition ->
                while (!listener.checkConfig()) {
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

    override fun buildAfterCommitHandler(chainId: Long): AfterCommitHandler {
        val baseHandler = super.buildAfterCommitHandler(chainId)

        return { bTrace: BlockTrace?, height: Long, blockTimestamp: Long ->
            try {
                val blockchainRid = blockchainProcesses[chainId]!!.blockchainEngine.getConfiguration().blockchainRid
                val committedBlockMessage = withReadConnection(storage, chainId) { eContext ->
                    val db = DatabaseAccess.of(eContext)
                    val blockRid = db.getBlockRID(eContext, height)!!
                    val blockHeader = db.getBlockHeader(eContext, blockRid)
                    val witnessData = db.getWitnessData(eContext, blockRid)
                    MsCommittedBlockMessage(
                            blockchainRid = blockchainRid.data,
                            blockRid = blockRid,
                            blockHeader = blockHeader,
                            witnessData = witnessData
                    )
                }
                (connectionManager as SubConnectionManager).sendMessageToMaster(chainId, committedBlockMessage)
            } catch (e: Exception) {
                logger.error(e) { "Error when sending committed block message: $e" }
            }
            subnodeBcCfgListeners[chainId]!!.commit(height, blockTimestamp)
            baseHandler(bTrace, height, blockTimestamp)
        }
    }
}
