package net.postchain.core

import net.postchain.common.reflection.newInstanceOf

class DefaultBlockchainConfigurationFactory : BlockchainConfigurationFactorySupplier {

    override fun supply(factoryName: String): BlockchainConfigurationFactory {
        return newInstanceOf(factoryName)
    }

}