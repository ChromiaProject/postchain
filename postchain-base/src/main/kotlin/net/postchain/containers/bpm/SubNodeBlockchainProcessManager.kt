package net.postchain.containers.bpm

import net.postchain.PostchainContext
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.AfterCommitHandler
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainEngine
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainRestartNotifier
import net.postchain.core.BlockchainState
import net.postchain.core.block.BlockTrace
import net.postchain.gtv.Gtv
import net.postchain.managed.BaseManagedNodeDataSource
import net.postchain.managed.ManagedBlockchainProcessManager
import net.postchain.network.mastersub.protocol.MsCommittedBlockMessage
import net.postchain.network.mastersub.subnode.SubConnectionManager
import net.postchain.network.mastersub.subnode.SubQueryHandler

class SubNodeBlockchainProcessManager(
        postchainContext: PostchainContext,
        blockchainInfrastructure: BlockchainInfrastructure,
        blockchainConfigProvider: BlockchainConfigurationProvider
) : ManagedBlockchainProcessManager(
        postchainContext,
        blockchainInfrastructure,
        blockchainConfigProvider
) {

    init {
        val queryRunner = { name: String, args: Gtv ->
            (connectionManager as SubConnectionManager).masterSubQueryManager.query(
                    null,
                    name,
                    args
            ).toCompletableFuture().get()
        }

        initManagedEnvironment(BaseManagedNodeDataSource(queryRunner, postchainContext.appConfig))
    }

    override fun createAndRegisterBlockchainProcess(
            chainId: Long,
            blockchainConfig: BlockchainConfiguration,
            engine: BlockchainEngine,
            restartNotifier: BlockchainRestartNotifier,
            blockchainState: BlockchainState
    ) {
        val subConnectionManager = connectionManager as SubConnectionManager
        subConnectionManager.preAddMsMessageHandler(chainId, SubQueryHandler(chainId, postchainContext.blockQueriesProvider, subConnectionManager))
        super.createAndRegisterBlockchainProcess(chainId, blockchainConfig, engine, restartNotifier, blockchainState)
    }

    override fun buildAfterCommitHandler(chainId: Long, blockchainConfig: BlockchainConfiguration): AfterCommitHandler {
        val baseHandler = super.buildAfterCommitHandler(chainId, blockchainConfig)

        return { bTrace: BlockTrace?, height: Long, blockTimestamp: Long ->
            val blockchainProcess = blockchainProcesses[chainId]
            if (blockchainProcess != null) {
                try {
                    val blockchainRid = blockchainProcess.blockchainEngine.getConfiguration().blockchainRid
                    val committedBlockMessage =
                            MsCommittedBlockMessage(
                                    blockchainRid = blockchainRid.data,
                                    blockHeight = height
                            )
                    (connectionManager as SubConnectionManager).sendMessageToMaster(chainId, committedBlockMessage)
                } catch (e: Exception) {
                    logger.error(e) { "Error when sending committed block message: $e" }
                }
            } else {
                logger.warn("No blockchain process found")
            }
            baseHandler(bTrace, height, blockTimestamp)
        }
    }
}
