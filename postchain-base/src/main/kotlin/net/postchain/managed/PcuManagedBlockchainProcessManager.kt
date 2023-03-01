// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.managed

import net.postchain.PostchainContext
import net.postchain.base.withReadConnection
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.core.BlockchainConfiguration
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManagerExtension
import net.postchain.ebft.worker.MessageProcessingLatch

open class PcuManagedBlockchainProcessManager(
        postchainContext: PostchainContext,
        blockchainInfrastructure: BlockchainInfrastructure,
        blockchainConfigProvider: BlockchainConfigurationProvider,
        bpmExtensions: List<BlockchainProcessManagerExtension> = listOf()
) : ManagedBlockchainProcessManager(
        postchainContext,
        blockchainInfrastructure,
        blockchainConfigProvider,
        bpmExtensions
) {
    override fun buildMessageProcessingLatch(blockchainConfig: BlockchainConfiguration): MessageProcessingLatch {
        // TODO: [POS-620]: height == 0L
        return MessageProcessingLatch {
            if (blockchainConfig.chainID == CHAIN0) {
                true // Chain0 runs in a (regular) managed mode
            } else {
                (blockchainConfigProvider as? PcuManagedBlockchainConfigurationProvider)?.run {
                    withReadConnection(storage, blockchainConfig.chainID) { ctx ->
                        isPendingBlockchainConfigurationApproved(ctx)
                    }
                } ?: false
            }
        }
    }
}