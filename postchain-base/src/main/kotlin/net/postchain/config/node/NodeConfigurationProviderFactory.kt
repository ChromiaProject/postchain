// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

import net.postchain.base.Storage
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfigProviders.File
import net.postchain.config.node.NodeConfigProviders.Legacy
import net.postchain.config.node.NodeConfigProviders.Managed
import net.postchain.config.node.NodeConfigProviders.Manual

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
        return when (appConfig.nodeConfigProvider.toLowerCase()) {
            Legacy.name.toLowerCase() -> LegacyNodeConfigurationProvider(appConfig)
            File.name.toLowerCase() -> FileNodeConfigurationProvider(appConfig)
            Manual.name.toLowerCase() -> ManualNodeConfigurationProvider(appConfig, storageFactory)
            Managed.name.toLowerCase() -> ManagedNodeConfigurationProvider(appConfig, storageFactory)
            else -> ManualNodeConfigurationProvider(appConfig, storageFactory)
        }
    }
}