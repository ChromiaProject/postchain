// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

import assertk.assert
import assertk.assertions.isInstanceOf
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import net.postchain.config.app.AppConfig
import net.postchain.config.node.NodeConfigurationProviderFactory.createProvider
import org.junit.jupiter.api.Test

class NodeConfigurationProviderFactoryTest {

    @Test
    fun createLegacyProvider() {
        val appConfig: AppConfig = mock {
            on { nodeConfigProvider } doReturn "legacy"
        }

        assert(createProvider(appConfig)).isInstanceOf(
                LegacyNodeConfigurationProvider::class)
    }

    @Test
    fun createManualProvider() {
        val appConfig: AppConfig = mock {
            on { nodeConfigProvider } doReturn "Manual"
        }

        val storageFactory = { _: AppConfig -> MockStorage.mock(emptyArray()) }

        assert(createProvider(appConfig, storageFactory)).isInstanceOf(
                ManualNodeConfigurationProvider::class)
    }

    @Test
    fun createManagedProvider() {
        val appConfig: AppConfig = mock {
            on { nodeConfigProvider } doReturn "Managed"
        }

        val storageFactory = { _: AppConfig -> MockStorage.mock(emptyArray()) }

        assert(createProvider(appConfig, storageFactory)).isInstanceOf(
                ManagedNodeConfigurationProvider::class)
    }

    @Test
    fun createDefaultProvider() {
        val appConfig: AppConfig = mock {
            on { nodeConfigProvider } doReturn "some-unknown-provider-here"
        }

        assert(createProvider(appConfig)).isInstanceOf(
                LegacyNodeConfigurationProvider::class)
    }

    @Test
    fun createEmptyProvider() {
        val appConfig: AppConfig = mock {
            on { nodeConfigProvider } doReturn ""
        }

        assert(createProvider(appConfig)).isInstanceOf(
                LegacyNodeConfigurationProvider::class)
    }
}