package net.postchain.client.cli

import net.postchain.client.core.PostchainClient
import net.postchain.client.core.PostchainClientProvider
import net.postchain.gtv.GtvFactory.gtv
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

private const val TEST_QUERY = "test_query"

internal class QueryCommandTest {

    private val invalidArgument = "1"

    @Test
    fun `Query must be done with args as dict`() {
        val client: PostchainClient = mock { }

        val testConfigPath = this::class.java.getResource("/config.cfg")!!.path
        val provider: PostchainClientProvider = mock {
            on { createClient(any()) } doReturn client
        }

        val command = QueryCommand(provider)

        assertThrows<IllegalArgumentException> {
            command.parse(listOf("--config", testConfigPath, TEST_QUERY, invalidArgument))
        }

        command.parse(listOf("--config", testConfigPath, TEST_QUERY))
        verify(client).query(TEST_QUERY, gtv(mapOf()))
    }
}