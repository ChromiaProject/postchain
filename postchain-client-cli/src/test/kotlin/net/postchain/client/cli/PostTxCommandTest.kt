package net.postchain.client.cli

import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.PostchainClient
import net.postchain.client.core.PostchainClientProvider
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.crypto.Secp256K1CryptoSystem
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

internal class PostTxCommandTest {

    @Test
    fun `Transactions are sent to client`() {
        val client: PostchainClient = mock {
            on { makeTransaction() } doReturn TransactionBuilder(this.mock, BlockchainRid.ZERO_RID, listOf("0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57".hexStringToByteArray()), Secp256K1CryptoSystem())
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
        verify(client).postTransactionSync(any())
    }
}