// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

import mu.KotlinLogging
import net.postchain.common.reflection.constructorOf
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfigProviders.*
import net.postchain.config.node.NodeConfigProviders.Companion.fromAlias
import net.postchain.core.Storage

object NodeConfigurationProviderFactory {
    /**
     * @param appConfig used to find the provider
     * @param storageFactory
     * @return the correct [NodeConfigurationProvider] based on [AppConfig]'s setting
     */
    fun createProvider(
        appConfig: AppConfig,
        storageFactory: (AppConfig) -> Storage
    ): NodeConfigurationProvider {
        if (appConfig.nodeConfigProvider.lowercase() == "legacy") {
            KotlinLogging.logger {  }.warn("Using deprecated legacy configuration provider, change to ${Properties.name.lowercase()}")
        }
        return when (fromAlias(appConfig.nodeConfigProvider)) {
            Properties -> PropertiesNodeConfigurationProvider(appConfig)
            Manual -> ManualNodeConfigurationProvider(appConfig, storageFactory)
            Managed -> ManagedNodeConfigurationProvider(appConfig, storageFactory)
            else -> constructorOf<NodeConfigurationProvider>(appConfig.nodeConfigProvider, AppConfig::class.java).newInstance(appConfig)
        }
    }
}