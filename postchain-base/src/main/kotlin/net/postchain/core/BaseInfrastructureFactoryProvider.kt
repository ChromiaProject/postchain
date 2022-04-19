// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.core

import net.postchain.base.BaseTestInfrastructureFactory
import net.postchain.common.reflection.newInstanceOf
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.containers.infra.MasterManagedEbftInfraFactory
import net.postchain.containers.infra.SubEbftInfraFactory
import net.postchain.core.Infrastructure.*
import net.postchain.ebft.BaseEBFTInfrastructureFactory
import net.postchain.managed.Chromia0InfrastructureFactory
import net.postchain.managed.ManagedEBFTInfrastructureFactory

/**
 * Provides infrastructure factory object based on `infrastructure` field of [net.postchain.config.node.NodeConfig]
 */
class BaseInfrastructureFactoryProvider : InfrastructureFactoryProvider {

    override fun createInfrastructureFactory(nodeConfigProvider: NodeConfigurationProvider): InfrastructureFactory {
        return when (val infra = nodeConfigProvider.getConfiguration().appConfig.infrastructure) {
            // Base
            in Ebft.key -> BaseEBFTInfrastructureFactory()
            in EbftManaged.key -> ManagedEBFTInfrastructureFactory()
            in EbftManagedChromia0.key -> Chromia0InfrastructureFactory()

            // Container chains
            in EbftManagedContainerMaster.key -> MasterManagedEbftInfraFactory()
            in EbftContainerSub.key -> SubEbftInfraFactory()
            in EbftManagedChromia0ContainerMaster.key -> newInstanceOf(infra) // TODO: [POS-129]: Will be implemented

            // Tests
            in BaseTest.key -> BaseTestInfrastructureFactory()

            else -> newInstanceOf(infra)
        }
    }
}