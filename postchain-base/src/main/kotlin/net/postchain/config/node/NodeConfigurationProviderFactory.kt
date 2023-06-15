// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

import mu.KLogging
import net.postchain.common.reflection.constructorOf
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfigProviders.Companion.fromAlias
import net.postchain.config.node.NodeConfigProviders.Manual
import net.postchain.config.node.NodeConfigProviders.Properties
import net.postchain.core.Storage

object NodeConfigurationProviderFactory : KLogging() {
    /**
     * @param appConfig used to find the provider
     * @param storageFactory
     * @return the correct [NodeConfigurationProvider] based on [AppConfig]'s setting
     */
    fun createProvider(
            appConfig: AppConfig,
            storage: Storage
    ): NodeConfigurationProvider {
        if (appConfig.nodeConfigProvider.lowercase() == "legacy") {
            logger.warn("Using deprecated legacy configuration provider, change to ${Properties.name.lowercase()}")
        }
        return when (fromAlias(appConfig.nodeConfigProvider)) {
            Properties -> PropertiesNodeConfigurationProvider(appConfig)
            Manual -> ManualNodeConfigurationProvider(appConfig, storage)
            else -> constructorOf<NodeConfigurationProvider>(appConfig.nodeConfigProvider, AppConfig::class.java).newInstance(appConfig)
        }
    }
}