package net.postchain.api.rest.infra

import assertk.assertThat
import assertk.assertions.contains
import net.postchain.PostchainContext
import net.postchain.config.app.AppConfig
import net.postchain.containers.api.DefaultMasterApiInfra
import org.apache.commons.configuration2.BaseConfiguration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class BaseApiInfrastructureTest {

    @Test
    fun test() {
        val testAppConfig = AppConfig(BaseConfiguration().apply {
            setProperty("database.sharedReadConcurrency", 10)
        })
        val postchainContext: PostchainContext = mock {
            on { appConfig } doReturn testAppConfig
        }
        val exception = assertThrows<IllegalArgumentException> {
            DefaultMasterApiInfra(
                    RestApiConfig(
                            "",
                            0,
                            0,
                            requestConcurrencyLocal = Int.MAX_VALUE,
                            requestConcurrencyExternal = 0,
                            maxRequestBodySize = 1000000000,
                            maxDataSize = 1000000000
                    ),
                    mock(),
                    postchainContext,
            )
        }
        assertThat(exception.message!!).contains("Calculated value for api.request-concurrency.external")
    }
}