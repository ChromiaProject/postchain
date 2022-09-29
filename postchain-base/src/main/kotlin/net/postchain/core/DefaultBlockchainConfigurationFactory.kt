package net.postchain.core

import net.postchain.common.reflection.newInstanceOf

class DefaultBlockchainConfigurationFactory : (String) -> BlockchainConfigurationFactory {

    override fun invoke(configurationFactoryName: String): BlockchainConfigurationFactory {
        return newInstanceOf(configurationFactoryName)
    }

}