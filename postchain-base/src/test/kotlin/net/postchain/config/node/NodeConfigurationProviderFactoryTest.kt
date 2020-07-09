// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

import assertk.assert
import assertk.assertions.isInstanceOf
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import net.postchain.config.app.AppConfig
import org.junit.Test

class NodeConfigurationProviderFactoryTest {

    @Test
    fun createLegacyProvider() {
        val appConfig: AppConfig = mock {
            on { nodeConfigProvider } doReturn "legacy"
        }
        val mockStorage = MockStorage.mock()
        val sut = NodeConfigurationProviderFactory { mockStorage }

        assert(sut.createProvider(appConfig)).isInstanceOf(
                LegacyNodeConfigurationProvider::class)
    }

    @Test
    fun createManualProvider() {
        val appConfig: AppConfig = mock {
            on { nodeConfigProvider } doReturn "Manual"
        }
        val mockStorage = MockStorage.mock()
        val sut = NodeConfigurationProviderFactory { mockStorage }

        assert(sut.createProvider(appConfig)).isInstanceOf(
                ManualNodeConfigurationProvider::class)
    }

    @Test
    fun createManagedProvider() {
        val appConfig: AppConfig = mock {
            on { nodeConfigProvider } doReturn "Managed"
        }
        val mockStorage = MockStorage.mock()
        val sut = NodeConfigurationProviderFactory { mockStorage }

        assert(sut.createProvider(appConfig)).isInstanceOf(
                ManagedNodeConfigurationProvider::class)
    }

    @Test
    fun createDefaultProvider() {
        val appConfig: AppConfig = mock {
            on { nodeConfigProvider } doReturn "some-unknown-provider-here"
        }
        val mockStorage = MockStorage.mock()
        val sut = NodeConfigurationProviderFactory { mockStorage }

        assert(sut.createProvider(appConfig)).isInstanceOf(
                LegacyNodeConfigurationProvider::class)
    }

    @Test
    fun createEmptyProvider() {
        val appConfig: AppConfig = mock {
            on { nodeConfigProvider } doReturn ""
        }
        val mockStorage = MockStorage.mock()
        val sut = NodeConfigurationProviderFactory { mockStorage }

        assert(sut.createProvider(appConfig)).isInstanceOf(
                LegacyNodeConfigurationProvider::class)
    }

}