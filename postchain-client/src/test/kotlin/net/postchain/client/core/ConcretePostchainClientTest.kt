package net.postchain.client.core

import net.postchain.client.config.STATUS_POLL_COUNT
import net.postchain.common.BlockchainRid
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

internal class ConcretePostchainClientTest {
    private var url = "http://localhost:7740"
    private lateinit var nodeResolver: PostchainNodeResolver
    private lateinit var httpResponse: CloseableHttpResponse
    private lateinit var httpClient: CloseableHttpClient
    private val brid = "EC03EDC6959E358B80D226D16A5BB6BC8EDE80EC17BD8BD0F21846C244AE7E8F"

    @BeforeEach
    fun setup() {
        nodeResolver = object : PostchainNodeResolver {
            override fun getNodeURL(blockchainRID: BlockchainRid) = url
        }

        httpResponse = mock {
            on { getCode() } doReturn 200
        }

        httpClient = mock {
            on { execute(any()) } doReturn httpResponse
        }
    }

    fun driveTestCorrectNumberOfAttempts(client: ConcretePostchainClient, numberExpected: Int) {
        client.makeTransaction()
            .addNop()
            .finish()
            .postSync(ConfirmationLevel.UNVERIFIED)

        // Verify
        verify(httpClient, times(1)).execute(any<HttpPost>())
        verify(httpClient, times(numberExpected)).execute(any<HttpGet>())
    }

    @Test
    fun `Max number of attempts by default`() {
        driveTestCorrectNumberOfAttempts(
            ConcretePostchainClient(nodeResolver, BlockchainRid.buildFromHex(brid), null, httpClient = httpClient),
            // If I didn't pass a max value, it defaults to RETRIEVE_TX_STATUS_ATTEMPTS = 20
            numberExpected = STATUS_POLL_COUNT)
    }

    @Test
    fun `Max number of attempts parameterized`() {
        driveTestCorrectNumberOfAttempts(
            ConcretePostchainClient(nodeResolver, BlockchainRid.buildFromHex(brid), null, 10, httpClient = httpClient),
            // If I pass a custom max value, verify it uses it
            numberExpected = 10)
    }
}
