// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.core

import net.postchain.base.BaseTestInfrastructureFactory
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.containers.infra.MasterManagedEbftInfraFactory
import net.postchain.containers.infra.SlaveEbftInfraFactory
import net.postchain.core.Infrastructure.*
import net.postchain.ebft.BaseEBFTInfrastructureFactory
import net.postchain.managed.Chromia0InfrastructureFactory
import net.postchain.managed.ManagedEBFTInfrastructureFactory

/**
 * Provides infrastructure factory object based on `infrastructure` field of [net.postchain.config.node.NodeConfig]
 */
class BaseInfrastructureFactoryProvider : InfrastructureFactoryProvider {

    override fun createInfrastructureFactory(nodeConfigProvider: NodeConfigurationProvider): InfrastructureFactory {
        val infra = nodeConfigProvider.getConfiguration().infrastructure

        val factoryClass = when (infra) {
            in Ebft.key -> BaseEBFTInfrastructureFactory::class.java
            in EbftManaged.key -> ManagedEBFTInfrastructureFactory::class.java
            in EbftManagedChromia0.key -> Chromia0InfrastructureFactory::class.java

            // Container chains
            in EbftManagedContainerMaster.key -> MasterManagedEbftInfraFactory::class.java
            in EbftContainerSlave.key -> SlaveEbftInfraFactory::class.java
            in EbftManagedChromia0ContainerMaster.key -> Class.forName(infra) // TODO: [POS-129]: Will be implemented

            in BaseTest.key -> BaseTestInfrastructureFactory::class.java

            else -> Class.forName(infra)
        }

        return factoryClass.newInstance() as InfrastructureFactory
    }
}