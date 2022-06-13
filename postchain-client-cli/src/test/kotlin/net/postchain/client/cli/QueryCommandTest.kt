package net.postchain.client.cli

import net.postchain.client.PostchainClientConfig
import net.postchain.client.core.PostchainClient
import net.postchain.client.core.PostchainClientProvider
import net.postchain.gtv.GtvFactory.gtv
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*

private const val TEST_QUERY = "test_query"

internal class QueryCommandTest {

    private val invalidArgument = "1"

    @Test
    fun `Query must be done with args as dict`() {
        val client: PostchainClient = mock { }

        val testConfigPath = this::class.java.getResource("/config.cfg")!!.path
        val testConfig = PostchainClientConfig.fromProperties(testConfigPath)
        val provider: PostchainClientProvider = mock {
            on { createClient(eq(testConfig.apiUrl), eq(testConfig.blockchainRid), any()) } doReturn client
        }

        val command = QueryCommand(provider)

        assertThrows<IllegalArgumentException> {
            command.parse(listOf("--config", testConfigPath, TEST_QUERY, invalidArgument))
        }

        command.parse(listOf("--config", testConfigPath, TEST_QUERY))
        verify(client).querySync(TEST_QUERY, gtv(mapOf()))
    }
}