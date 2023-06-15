// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.core

import net.postchain.common.reflection.newInstanceOf
import net.postchain.config.app.AppConfig
import net.postchain.containers.infra.MasterManagedEbftInfraFactory
import net.postchain.containers.infra.SubEbftInfraFactory
import net.postchain.core.Infrastructure.Ebft
import net.postchain.core.Infrastructure.EbftContainerSub
import net.postchain.core.Infrastructure.EbftManaged
import net.postchain.core.Infrastructure.EbftManagedChromia0
import net.postchain.core.Infrastructure.EbftManagedChromia0ContainerMaster
import net.postchain.core.Infrastructure.EbftManagedContainerMaster
import net.postchain.ebft.BaseEBFTInfrastructureFactory
import net.postchain.managed.Chromia0InfrastructureFactory
import net.postchain.managed.ManagedEBFTInfrastructureFactory

/**
 * Provides infrastructure factory object based on `infrastructure` field of [net.postchain.config.node.NodeConfig]
 */
object BaseInfrastructureFactoryProvider : InfrastructureFactoryProvider {

    override fun createInfrastructureFactory(appConfig: AppConfig): InfrastructureFactory {
        return when (val infra = appConfig.infrastructure) {
            // Base
            in Ebft.key -> BaseEBFTInfrastructureFactory()
            in EbftManaged.key -> ManagedEBFTInfrastructureFactory()
            in EbftManagedChromia0.key -> Chromia0InfrastructureFactory()

            // Container chains
            in EbftManagedContainerMaster.key -> MasterManagedEbftInfraFactory()
            in EbftContainerSub.key -> SubEbftInfraFactory()
            in EbftManagedChromia0ContainerMaster.key -> newInstanceOf(infra) // TODO: [POS-129]: Will be implemented

            else -> newInstanceOf(infra)
        }
    }
}