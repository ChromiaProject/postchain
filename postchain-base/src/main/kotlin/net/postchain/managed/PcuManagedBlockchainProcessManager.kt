// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.managed

import net.postchain.PostchainContext
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
            (blockchainConfigProvider as? PcuManagedBlockchainConfigurationProvider)
                    ?.isPendingBlockchainConfigurationApproved(blockchainConfig.blockchainRid, 0L) ?: false
        }
    }
}