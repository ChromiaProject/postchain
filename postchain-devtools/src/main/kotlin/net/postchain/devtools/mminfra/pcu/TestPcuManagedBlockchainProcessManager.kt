package net.postchain.devtools.mminfra.pcu

import net.postchain.PostchainContext
import net.postchain.base.withReadConnection
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManagerExtension
import net.postchain.devtools.mminfra.TestManagedBlockchainProcessManager
import net.postchain.ebft.worker.MessageProcessingLatch
import net.postchain.managed.ManagedNodeDataSource
import net.postchain.managed.PcuManagedBlockchainConfigurationProvider

class TestPcuManagedBlockchainProcessManager(
        postchainContext: PostchainContext,
        blockchainInfrastructure: BlockchainInfrastructure,
        blockchainConfigProvider: BlockchainConfigurationProvider,
        testDataSource: ManagedNodeDataSource,
        bpmExtensions: List<BlockchainProcessManagerExtension> = listOf()
) : TestManagedBlockchainProcessManager(
        postchainContext,
        blockchainInfrastructure,
        blockchainConfigProvider,
        testDataSource,
        bpmExtensions
) {

    override fun buildMessageProcessingLatch(blockchainConfig: BlockchainConfiguration): MessageProcessingLatch {
        // TODO: [POS-620]: height == 0L
        return MessageProcessingLatch {
            if (blockchainConfig.chainID == CHAIN0) {
                true // Chain0 runs in a (regular) managed mode
            } else {
                withReadConnection(storage, blockchainConfig.chainID) { ctx ->
                    (blockchainConfigProvider as? PcuManagedBlockchainConfigurationProvider)
                            ?.isPendingBlockchainConfigurationApproved(ctx) ?: false
                }
            }
        }
    }

}