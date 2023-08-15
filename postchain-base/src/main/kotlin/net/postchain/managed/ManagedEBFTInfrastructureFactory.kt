// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.managed

import net.postchain.PostchainContext
import net.postchain.config.app.AppConfig
import net.postchain.config.blockchain.BlockchainConfigurationProvider
import net.postchain.config.node.ManagedNodeConfigurationProvider
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.BlockchainInfrastructure
import net.postchain.core.BlockchainProcessManager
import net.postchain.core.Storage
import net.postchain.ebft.BaseEBFTInfrastructureFactory

open class ManagedEBFTInfrastructureFactory : BaseEBFTInfrastructureFactory() {

    final override fun makeNodeConfigurationProvider(appConfig: AppConfig, storage: Storage): NodeConfigurationProvider {
        return ManagedNodeConfigurationProvider(appConfig, storage)
    }

    override fun makeBlockchainConfigurationProvider(): BlockchainConfigurationProvider {
        return ManagedBlockchainConfigurationProvider()
    }

    override fun makeProcessManager(
            postchainContext: PostchainContext,
            blockchainInfrastructure: BlockchainInfrastructure,
            blockchainConfigurationProvider: BlockchainConfigurationProvider
    ): BlockchainProcessManager {

        return ManagedBlockchainProcessManager(postchainContext,
                blockchainInfrastructure,
                blockchainConfigurationProvider,
                getProcessManagerExtensions(postchainContext, blockchainInfrastructure)
        )
    }
}