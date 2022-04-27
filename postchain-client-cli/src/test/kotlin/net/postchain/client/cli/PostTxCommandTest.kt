package net.postchain.client.cli

import net.postchain.client.core.ConfirmationLevel
import net.postchain.client.core.GTXTransactionBuilder
import net.postchain.client.core.PostchainClient
import net.postchain.client.core.PostchainClientProvider
import net.postchain.client.testConfig
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

        val provider: PostchainClientProvider = mock {
            on { createClient(eq(testConfig.apiUrl), eq(testConfig.blockchainRid), any()) } doReturn client
        }

        val command = PostTxCommand(provider)

        command.runInternal(testConfig, false, "test_tx", gtv(1))

        verify(client).makeTransaction()
        verify(txBuilder).postSync(ConfirmationLevel.NO_WAIT)
    }
}