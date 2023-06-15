// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.config.node

import assertk.assertThat
import assertk.assertions.isInstanceOf
import net.postchain.common.exception.UserMistake
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
        assertThat(NodeConfigurationProviderFactory.createProvider(appConfig) { mockStorage.storage }).isInstanceOf(expected)
    }

    companion object {
        @JvmStatic
        fun testData() = listOf(
                arrayOf("legacy", PropertiesNodeConfigurationProvider::class), // Deprecated and will be removed in 3.6?
                arrayOf("properties", PropertiesNodeConfigurationProvider::class),
                arrayOf("Manual", ManualNodeConfigurationProvider::class), // case insensitive
        )
    }

    @Test
    fun `Test invalid configuration`() {
        val appConfig: AppConfig = mock {
            on { nodeConfigProvider } doReturn "some-unknown-provider-here"
        }
        val mockStorage = MockStorage.mockAppContext()

        assertThrows<UserMistake> {
            NodeConfigurationProviderFactory.createProvider(appConfig) { mockStorage.storage }
        }
    }
}