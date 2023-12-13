package net.postchain.containers.infra

import mu.KLogging
import net.postchain.PostchainContext
import net.postchain.base.PeerCommConfiguration
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.containers.infra.DefaultSubSyncInfra.UnarchivingBlockchainProcessType.FORCE_READONLY
import net.postchain.containers.infra.DefaultSubSyncInfra.UnarchivingBlockchainProcessType.MUTED_FORCE_READONLY
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
import net.postchain.managed.UnarchivingBlockchainNodeInfo
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
        VALIDATOR_OR_REPLICA, FORCE_READONLY, MUTED_FORCE_READONLY
    }

    override fun buildXCommunicationManager(
            blockchainConfigProvider: BlockchainConfigurationProvider,
            blockchainConfig: BlockchainConfiguration,
            relevantPeerCommConfig: PeerCommConfiguration,
            blockchainRid: BlockchainRid
    ): CommunicationManager<EbftMessage> {
        val (_, procType) = getUnarchivingBlockchainInfoAndProcessType(blockchainConfigProvider, blockchainConfig)

        return if (procType == MUTED_FORCE_READONLY)
            MutedCommunicationManager()
        else
            super.buildXCommunicationManager(blockchainConfigProvider, blockchainConfig, relevantPeerCommConfig, blockchainRid)
    }

    override fun createUnarchivingBlockchainProcess(
            workerContext: WorkerContext,
            blockchainConfigProvider: BlockchainConfigurationProvider,
            blockchainConfig: BlockchainConfiguration,
            blockchainState: BlockchainState,
            iAmASigner: Boolean
    ): BlockchainProcess {
        val (ubcInfo, procType) = getUnarchivingBlockchainInfoAndProcessType(blockchainConfigProvider, blockchainConfig)
        return when {
            procType == FORCE_READONLY || procType == MUTED_FORCE_READONLY -> ForceReadOnlyBlockchainProcess(workerContext, blockchainState, ubcInfo?.upToHeight)
            iAmASigner -> ValidatorBlockchainProcess(workerContext, getStartWithFastSyncValue(blockchainConfig.chainID), blockchainState)
            else -> ReadOnlyBlockchainProcess(workerContext, blockchainState)
        }
    }

    private fun getUnarchivingBlockchainInfoAndProcessType(
            blockchainConfigProvider: BlockchainConfigurationProvider,
            blockchainConfig: BlockchainConfiguration
    ): Pair<UnarchivingBlockchainNodeInfo?, UnarchivingBlockchainProcessType> {
        val ubcInfo = (blockchainConfigProvider as? ManagedBlockchainConfigurationProvider)
                ?.getUnarchivingBlockchainNodeInfo(blockchainConfig.blockchainRid)
        logger.error { "ubcInfo: $ubcInfo" }
        logger.error { "blockchainConfigProvider is ${blockchainConfigProvider.javaClass.name}" }

        val type = if (ubcInfo == null) {
            VALIDATOR_OR_REPLICA
        } else {
            when {
                ubcInfo.isSourceNode && !ubcInfo.isDestinationNode -> FORCE_READONLY
                !ubcInfo.isSourceNode && ubcInfo.isDestinationNode -> VALIDATOR_OR_REPLICA
                // bcInfo.isSourceNode && bcInfo.isDestinationNode
                containerNodeConfig.directoryContainer == ubcInfo.sourceContainer -> FORCE_READONLY
                else -> {
                    val lastBlockHeight = withReadConnection(postchainContext.sharedStorage, blockchainConfig.chainID) {
                        DatabaseAccess.of(it).getLastBlockHeight(it)
                    }
                    if (lastBlockHeight < ubcInfo.upToHeight) MUTED_FORCE_READONLY else VALIDATOR_OR_REPLICA
                }
            }
        }

        return ubcInfo to type
    }
}