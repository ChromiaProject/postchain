// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.core

import net.postchain.base.BaseL2TestInfrastructureFactory
import net.postchain.base.BaseTestInfrastructureFactory
import net.postchain.config.node.NodeConfigurationProvider
import net.postchain.ebft.BaseEBFTInfrastructureFactory
import net.postchain.l2.BaseL2EBFTInfrastructureFactory

/**
 * Provides infrastructure factory object based on `infrastructure` field of [net.postchain.config.node.NodeConfig]
 */
class BaseInfrastructureFactoryProvider : InfrastructureFactoryProvider {

    override fun createInfrastructureFactory(nodeConfigProvider: NodeConfigurationProvider): InfrastructureFactory {
        val factoryClass = when (val infrastructure = nodeConfigProvider.getConfiguration().infrastructure) {
            Infrastructures.BaseEbft.secondName.toLowerCase() -> BaseEBFTInfrastructureFactory::class.java
            Infrastructures.BaseL2Ebft.secondName.toLowerCase() -> BaseL2EBFTInfrastructureFactory::class.java
            Infrastructures.BaseTest.secondName.toLowerCase() -> BaseTestInfrastructureFactory::class.java
            Infrastructures.BaseL2Test.secondName.toLowerCase() -> BaseL2TestInfrastructureFactory::class.java
            else -> Class.forName(infrastructure)
        }
        return factoryClass.newInstance() as InfrastructureFactory
    }
}