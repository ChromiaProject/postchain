// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

import mu.KotlinLogging
import net.postchain.base.Storage
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfigProviders.Companion.fromAlias
import net.postchain.config.node.NodeConfigProviders.Explicit
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
        if (appConfig.nodeConfigProvider.toLowerCase() == "legacy") {
            KotlinLogging.logger {  }.warn("Using deprecated legacy configuration provider, change to ${Explicit.name.toLowerCase()}")
        }
        return when (fromAlias(appConfig.nodeConfigProvider)) {
            Explicit -> ExplicitPeerListNodeConfigurationProvider(appConfig)
            Manual -> ManualNodeConfigurationProvider(appConfig, storageFactory)
            Managed -> ManagedNodeConfigurationProvider(appConfig, storageFactory)
            else -> Class.forName(appConfig.nodeConfigProvider).getDeclaredConstructor(AppConfig::class.java).newInstance(appConfig) as NodeConfigurationProvider
        }
    }
}