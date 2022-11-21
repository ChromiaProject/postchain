package net.postchain.client.cli

import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.ConcretePostchainClient
import net.postchain.client.core.PostchainClientProvider
import org.http4k.client.AsyncHttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify

internal class PostTxCommandTest {

    @Test
    fun `Transactions are sent to client`() {
        val testConfigPath = this::class.java.getResource("/config.cfg")!!.path
        val testConfig = PostchainClientConfig.fromProperties(testConfigPath)

        val httpClient: AsyncHttpHandler = object : AsyncHttpHandler {
            override fun invoke(request: Request, fn: (Response) -> Unit) {
                fn(Response(Status.OK))
            }
        }

        val mockClient = spy(ConcretePostchainClient(testConfig, httpClient))
        val provider: PostchainClientProvider = mock {
            on { createClient(any()) } doReturn mockClient
        }

        val command = PostTxCommand(provider)

        // Verify various argument types are working, e.g. strings containing spaces
        command.parse(
            listOf(
                "--config", testConfigPath, "test_tx",
                "1",
                "foo",
                "foo bar",
                "[1,foo]",
                "{a->b}"
            )
        )

        verify(mockClient).transactionBuilder()
        verify(mockClient).postTransaction(any())
    }
}