package net.postchain.client.cli

import net.postchain.client.core.PostchainClient
import net.postchain.client.core.PostchainClientProvider
import net.postchain.client.testConfig
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvFactory.gtv
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*

private const val TEST_QUERY = "test_query"

internal class QueryCommandTest {

    private val invalidArgument = gtv(1)
    private val validArgument: GtvDictionary = gtv(mapOf())

    @Test
    fun `Query must be done with args as dict`() {
        val client: PostchainClient = mock { }

        val provider: PostchainClientProvider = mock {
            on { createClient(eq(testConfig.apiUrl), eq(testConfig.blockchainRid), any()) } doReturn client
        }

        val command = QueryCommand(provider)

        assertThrows<IllegalArgumentException> {
            command.runInternal(testConfig, TEST_QUERY, invalidArgument)
        }

        command.runInternal(testConfig, TEST_QUERY, validArgument)
        verify(client).querySync(TEST_QUERY, validArgument)
    }
}