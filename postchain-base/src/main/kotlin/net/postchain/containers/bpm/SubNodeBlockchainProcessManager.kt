package net.postchain.containers.bpm

import net.postchain.PostchainContext
import net.postchain.base.BaseBlockchainProcessManager
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.containers.bpm.bcconfig.BlockWiseSubnodeBlockchainConfigListener
import net.postchain.containers.bpm.bcconfig.BlockchainConfigVerifier
import net.postchain.containers.bpm.bcconfig.SubnodeBlockchainConfigListener
import net.postchain.containers.bpm.bcconfig.SubnodeBlockchainConfigurationConfig
import net.postchain.core.AfterCommitHandler
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.block.BlockTrace
import net.postchain.debug.BlockchainProcessName
import net.postchain.ebft.worker.MessageProcessingLatch
import net.postchain.network.mastersub.protocol.MsCommittedBlockMessage
import net.postchain.network.mastersub.subnode.SubConnectionManager
import net.postchain.network.mastersub.subnode.SubQueryHandler
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

    private val subnodeBcCfgConfig = SubnodeBlockchainConfigurationConfig.fromAppConfig(appConfig)
    private val subnodeBcCfgListeners: MutableMap<Long, SubnodeBlockchainConfigListener> = ConcurrentHashMap()
    private val configVerifier = BlockchainConfigVerifier(appConfig)

    override fun createAndRegisterBlockchainProcess(
            chainId: Long,
            blockchainConfig: BlockchainConfiguration,
            processName: BlockchainProcessName,
            engine: BlockchainEngine,
            messageProcessingLatch: MessageProcessingLatch
    ) {
        val subConnectionManager = connectionManager as SubConnectionManager
        subnodeBcCfgListeners[chainId] = BlockWiseSubnodeBlockchainConfigListener(
                subnodeBcCfgConfig,
                configVerifier,
                chainId,
                blockchainConfig.blockchainRid,
                subConnectionManager,
                blockchainConfigProvider,
                storage
        )
        subConnectionManager.preAddMsMessageHandler(chainId, SubQueryHandler(chainId, postchainContext.blockQueriesProvider, subConnectionManager))
        super.createAndRegisterBlockchainProcess(chainId, blockchainConfig, processName, engine, messageProcessingLatch)
    }

    override fun stopAndUnregisterBlockchainProcess(chainId: Long, restart: Boolean, bTrace: BlockTrace?) {
        subnodeBcCfgListeners.remove(chainId)
        super.stopAndUnregisterBlockchainProcess(chainId, restart, bTrace)
    }

    override fun buildAfterCommitHandler(chainId: Long): AfterCommitHandler {
        val baseHandler = super.buildAfterCommitHandler(chainId)

        return { bTrace: BlockTrace?, height: Long, blockTimestamp: Long ->
            val blockchainProcess = blockchainProcesses[chainId]
            if (blockchainProcess != null) {
                try {
                    val blockchainRid = blockchainProcess.blockchainEngine.getConfiguration().blockchainRid
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
            } else {
                logger.warn("No blockchain process for $chainId")
            }
            val subnodeBlockchainConfigListener = subnodeBcCfgListeners[chainId]
            if (subnodeBlockchainConfigListener != null) {
                subnodeBlockchainConfigListener.commit(height)
            } else {
                logger.warn("No subnode config listener for $chainId")
            }
            baseHandler(bTrace, height, blockTimestamp)
        }
    }
}
