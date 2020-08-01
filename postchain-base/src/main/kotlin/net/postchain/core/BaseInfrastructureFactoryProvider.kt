// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.core

import net.postchain.base.BaseTestInfrastructureFactory
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.core.Infrastructures.*
import net.postchain.ebft.BaseEBFTInfrastructureFactory
import net.postchain.extchains.infra.MasterExtChainsManagedEbftInfrastructureFactory
import net.postchain.extchains.infra.SlaveExtChainsEbftInfrastructureFactory
import net.postchain.managed.Chromia0InfrastructureFactory
import net.postchain.managed.ManagedEBFTInfrastructureFactory

/**
 * Provides infrastructure factory object based on `infrastructure` field of [net.postchain.config.node.NodeConfig]
 */
class BaseInfrastructureFactoryProvider : InfrastructureFactoryProvider {

    override fun createInfrastructureFactory(nodeConfigProvider: NodeConfigurationProvider): InfrastructureFactory {
        val infra = nodeConfigProvider.getConfiguration().infrastructure

        val factoryClass = when (infra) {
            // base/ebft, base-ebft
            BaseEbft.key, Ebft.key -> BaseEBFTInfrastructureFactory::class.java

            // base/test
            BaseTest.key -> BaseTestInfrastructureFactory::class.java

            // ebft-managed
            EbftManaged.key -> ManagedEBFTInfrastructureFactory::class.java

            // ebft-managed-chromia0
            EbftManagedChromia0.key, Chromia0.key -> Chromia0InfrastructureFactory::class.java

            // ebft-managed-master, ebft-managed-master-chromia0
            EbftManagedMaster.key -> MasterExtChainsManagedEbftInfrastructureFactory::class.java
            EbftSlave.key -> SlaveExtChainsEbftInfrastructureFactory::class.java
            EbftManagedMasterChromia0.key -> Class.forName(infra) // TODO: [POS-129]: Will be finished

            else -> Class.forName(infra)
        }

        return factoryClass.newInstance() as InfrastructureFactory
    }
}