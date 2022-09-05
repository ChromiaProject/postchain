package net.postchain.client.cli

import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.GTXTransactionBuilder
import net.postchain.client.core.PostchainClient
import net.postchain.client.core.PostchainClientProvider
import net.postchain.gtv.GtvFactory.gtv
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

internal class PostTxCommandTest {

    @Test
    fun `Transactions are sent to client`() {
        val txBuilder: GTXTransactionBuilder = mock {}
        val client: PostchainClient = mock {
            on { makeTransaction() } doReturn txBuilder
        }

        val testConfigPath = this::class.java.getResource("/config.cfg")!!.path
        val testConfig = PostchainClientConfig.fromProperties(testConfigPath)
        val provider: PostchainClientProvider = mock {
            on { createClient(eq(testConfig.apiUrl), eq(testConfig.blockchainRid), any(), any(), any()) } doReturn client
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

        verify(client).makeTransaction()
        verify(txBuilder).addOperation(
            "test_tx",
            gtv(1),
            gtv("foo"),
            gtv("foo bar"),
            gtv(listOf(gtv(1), gtv("foo"))),
            gtv(mapOf("a" to gtv("b")))
        )
        verify(txBuilder).postSync()
    }
}