package net.postchain.managed

import net.postchain.config.app.AppConfig
import net.postchain.managed.config.Chain0BlockchainConfigurationFactory
import net.postchain.managed.config.DappBlockchainConfigurationFactory

class ExtendedChain0BcCfgFactory(appConfig: AppConfig) : Chain0BlockchainConfigurationFactory(appConfig)

class ExtendedDappBcCfgFactory(dataSource: ManagedNodeDataSource) : DappBlockchainConfigurationFactory(dataSource)

