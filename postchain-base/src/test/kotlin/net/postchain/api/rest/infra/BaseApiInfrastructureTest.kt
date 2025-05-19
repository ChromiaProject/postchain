package net.postchain.api.rest.infra

import assertk.assertThat
import assertk.assertions.contains
import net.postchain.containers.api.DefaultMasterApiInfra
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock

class BaseApiInfrastructureTest {

    @Test
    fun test() {
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
                    mock(),
            )
        }
        assertThat(exception.message!!).contains("Calculated value for api.request-concurrency.external")
    }
}