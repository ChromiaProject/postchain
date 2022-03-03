// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

import assertk.assert
import assertk.assertions.isInstanceOf
import net.postchain.config.app.AppConfig
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class NodeConfigurationProviderFactoryTest {

    @Test
    fun createLegacyProvider() {
        val appConfig: AppConfig = mock {
            on { nodeConfigProvider } doReturn "legacy"
        }
        val mockStorage = MockStorage.mockAppContext()

        assert(NodeConfigurationProviderFactory.createProvider(appConfig) { mockStorage }).isInstanceOf(
                LegacyNodeConfigurationProvider::class)
    }

    @Test
    fun createManualProvider() {
        val appConfig: AppConfig = mock {
            on { nodeConfigProvider } doReturn "Manual"
        }
        val mockStorage = MockStorage.mockAppContext()

        assert(NodeConfigurationProviderFactory.createProvider(appConfig) { mockStorage }).isInstanceOf(
                ManualNodeConfigurationProvider::class)
    }

    @Test
    fun createManagedProvider() {
        val appConfig: AppConfig = mock {
            on { nodeConfigProvider } doReturn "Managed"
        }
        val mockStorage = MockStorage.mockAppContext()

        assert(NodeConfigurationProviderFactory.createProvider(appConfig) { mockStorage }).isInstanceOf(
                ManagedNodeConfigurationProvider::class)
    }

    @Test
    fun createDefaultProvider() {
        val appConfig: AppConfig = mock {
            on { nodeConfigProvider } doReturn "some-unknown-provider-here"
        }
        val mockStorage = MockStorage.mockAppContext()

        assert(NodeConfigurationProviderFactory.createProvider(appConfig) { mockStorage }).isInstanceOf(
                ManualNodeConfigurationProvider::class)
    }

    @Test
    fun createEmptyProvider() {
        val appConfig: AppConfig = mock {
            on { nodeConfigProvider } doReturn ""
        }
        val mockStorage = MockStorage.mockAppContext()

        assert(NodeConfigurationProviderFactory.createProvider(appConfig) { mockStorage }).isInstanceOf(
                ManualNodeConfigurationProvider::class)
    }
}