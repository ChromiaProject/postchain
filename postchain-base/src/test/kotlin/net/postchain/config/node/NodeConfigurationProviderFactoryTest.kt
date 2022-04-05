// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

import assertk.assert
import assertk.assertions.isInstanceOf
import net.postchain.config.app.AppConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.reflect.KClass

class NodeConfigurationProviderFactoryTest {

    @ParameterizedTest
    @MethodSource("testData")
    fun `Test valid node configurations`(config: String, expected: KClass<NodeConfigurationProvider>) {
        val appConfig: AppConfig = mock {
            on { nodeConfigProvider } doReturn config
        }
        val mockStorage = MockStorage.mockAppContext()
        assert(NodeConfigurationProviderFactory.createProvider(appConfig) { mockStorage }).isInstanceOf(expected)
    }

    companion object {
        @JvmStatic
        fun testData() = listOf(
                arrayOf("legacy", ExplicitPeerListNodeConfigurationProvider::class),
                arrayOf("Manual", ManualNodeConfigurationProvider::class),
                arrayOf("ManageD", ManagedNodeConfigurationProvider::class),
                arrayOf("", ExplicitPeerListNodeConfigurationProvider::class)
        )
    }

    @Test
    fun `Test invalid configuration`() {
        val appConfig: AppConfig = mock {
            on { nodeConfigProvider } doReturn "some-unknown-provider-here"
        }
        val mockStorage = MockStorage.mockAppContext()

        assertThrows<ClassNotFoundException> {
            NodeConfigurationProviderFactory.createProvider(appConfig) { mockStorage }
        }
    }
}