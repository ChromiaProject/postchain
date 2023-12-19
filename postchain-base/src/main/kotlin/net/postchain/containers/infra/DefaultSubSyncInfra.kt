package net.postchain.containers.infra

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.base.HistoricBlockchainContext
import net.postchain.base.PeerCommConfiguration
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.containers.infra.DefaultSubSyncInfra.UnarchivingBlockchainProcessType.FORCE_READONLY
import net.postchain.containers.infra.DefaultSubSyncInfra.UnarchivingBlockchainProcessType.VALIDATOR_OR_REPLICA
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainProcess
import net.postchain.core.BlockchainState
import net.postchain.ebft.EBFTSynchronizationInfrastructure
import net.postchain.ebft.message.EbftMessage
import net.postchain.ebft.worker.ForceReadOnlyBlockchainProcess
import net.postchain.ebft.worker.ReadOnlyBlockchainProcess
import net.postchain.ebft.worker.ValidatorBlockchainProcess
import net.postchain.ebft.worker.WorkerContext
import net.postchain.managed.ManagedBlockchainConfigurationProvider
import net.postchain.managed.MigratingBlockchainNodeInfo
import net.postchain.network.CommunicationManager
import net.postchain.network.mastersub.subnode.MutedCommunicationManager
import net.postchain.network.peer.PeersCommConfigFactory

class DefaultSubSyncInfra(
        postchainContext: PostchainContext,
        peersCommConfigFactory: PeersCommConfigFactory,
        val containerNodeConfig: ContainerNodeConfig
) : EBFTSynchronizationInfrastructure(postchainContext, peersCommConfigFactory) {

    companion object : KLogging()

    private enum class UnarchivingBlockchainProcessType {
        VALIDATOR_OR_REPLICA, FORCE_READONLY
    }

    override fun buildXCommunicationManager(
            blockchainConfigProvider: BlockchainConfigurationProvider,
            blockchainConfig: BlockchainConfiguration,
            relevantPeerCommConfig: PeerCommConfiguration,
            blockchainRid: BlockchainRid
    ): CommunicationManager<EbftMessage> {
        return if (isMigrationWithinNodeToCurrentContainer(blockchainConfigProvider, blockchainConfig))
            MutedCommunicationManager()
        else
            super.buildXCommunicationManager(blockchainConfigProvider, blockchainConfig, relevantPeerCommConfig, blockchainRid)
    }

    override fun createRunningBlockchainProcess(
            workerContext: WorkerContext,
            historicBlockchainContext: HistoricBlockchainContext?,
            blockchainConfigProvider: BlockchainConfigurationProvider,
            blockchainConfig: BlockchainConfiguration,
            blockchainState: BlockchainState,
            iAmASigner: Boolean
    ): BlockchainProcess {
        val (continueMigration, info) = shouldContinueMigrationWithinNodeFromCurrentContainer(blockchainConfigProvider, blockchainConfig)
        return if (continueMigration) {
            ForceReadOnlyBlockchainProcess(workerContext, blockchainState, info?.upToHeight)
        } else {
            super.createRunningBlockchainProcess(
                    workerContext, historicBlockchainContext, blockchainConfigProvider, blockchainConfig, blockchainState, iAmASigner)
        }
    }

    override fun createPausedBlockchainProcess(
            workerContext: WorkerContext,
            blockchainConfigProvider: BlockchainConfigurationProvider,
            blockchainConfig: BlockchainConfiguration,
            blockchainState: BlockchainState
    ): BlockchainProcess {
        val (continueMigration, info) = shouldContinueMigrationWithinNodeFromCurrentContainer(blockchainConfigProvider, blockchainConfig)
        return if (continueMigration) {
            ForceReadOnlyBlockchainProcess(workerContext, blockchainState, info?.upToHeight)
        } else {
            super.createPausedBlockchainProcess(workerContext, blockchainConfigProvider, blockchainConfig, blockchainState)
        }
    }

    private fun shouldContinueMigrationWithinNodeFromCurrentContainer(
            blockchainConfigProvider: BlockchainConfigurationProvider, blockchainConfig: BlockchainConfiguration
    ): Pair<Boolean, MigratingBlockchainNodeInfo?> {
        val info = (blockchainConfigProvider as? ManagedBlockchainConfigurationProvider)
                ?.getMigratingBlockchainNodeInfo(blockchainConfig.blockchainRid)
                ?: return false to null

        val res = if (info.isSourceNode && info.isDestinationNode) {
            if (containerNodeConfig.directoryContainer == info.sourceContainer) { // src container
                info.upToHeight != -1L
            } else { // dst container
                shouldContinueMigration(info.upToHeight, blockchainConfig.chainID)
            }
        } else false

        return res to info
    }

    override fun createUnarchivingBlockchainProcess(
            workerContext: WorkerContext,
            blockchainConfigProvider: BlockchainConfigurationProvider,
            blockchainConfig: BlockchainConfiguration,
            blockchainState: BlockchainState,
            iAmASigner: Boolean
    ): BlockchainProcess {
        val info = (blockchainConfigProvider as? ManagedBlockchainConfigurationProvider)
                ?.getMigratingBlockchainNodeInfo(blockchainConfig.blockchainRid)

        val type = if (info == null) {
            VALIDATOR_OR_REPLICA
        } else {
            when {
                info.isSourceNode && !info.isDestinationNode -> FORCE_READONLY
                !info.isSourceNode && info.isDestinationNode -> VALIDATOR_OR_REPLICA
                info.isSourceNode && info.isDestinationNode -> {
                    if (containerNodeConfig.directoryContainer == info.sourceContainer) // src container
                        FORCE_READONLY
                    else { // dst container
                        if (shouldContinueMigration(info.upToHeight, blockchainConfig.chainID))
                            FORCE_READONLY else VALIDATOR_OR_REPLICA
                    }
                }

                else -> throw ProgrammerMistake("Illegal state for node: $info")
            }
        }

        return when {
            type == FORCE_READONLY -> ForceReadOnlyBlockchainProcess(workerContext, blockchainState, info?.upToHeight)
            iAmASigner -> ValidatorBlockchainProcess(workerContext, getStartWithFastSyncValue(blockchainConfig.chainID), blockchainState)
            else -> ReadOnlyBlockchainProcess(workerContext, blockchainState)
        }
    }

    private fun isMigrationWithinNodeToCurrentContainer(
            blockchainConfigProvider: BlockchainConfigurationProvider,
            blockchainConfig: BlockchainConfiguration
    ): Boolean {
        val info = (blockchainConfigProvider as? ManagedBlockchainConfigurationProvider)
                ?.getMigratingBlockchainNodeInfo(blockchainConfig.blockchainRid)
                ?: return false

        val withinNode = info.isSourceNode && info.isDestinationNode
        val iAmInDestinationContainer = containerNodeConfig.directoryContainer == info.destinationContainer
        val continueMigration = shouldContinueMigration(info.upToHeight, blockchainConfig.chainID)

        return withinNode && iAmInDestinationContainer && continueMigration
    }

    private fun shouldContinueMigration(upToHeight: Long, chainId: Long): Boolean {
        return upToHeight == -1L || withReadConnection(postchainContext.sharedStorage, chainId) {
            DatabaseAccess.of(it).getLastBlockHeight(it) < upToHeight
        }
    }
}