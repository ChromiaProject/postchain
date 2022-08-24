package net.postchain.client.cli

import net.postchain.client.core.ConfirmationLevel
import net.postchain.client.core.GTXTransactionBuilder
import net.postchain.client.core.PostchainClient
import net.postchain.client.core.ConcretePostchainClient
import net.postchain.client.core.PostchainClientProvider
import net.postchain.client.core.PostchainNodeResolver
import net.postchain.client.core.ConcretePostchainClientProvider
import net.postchain.client.core.DefaultSigner
import net.postchain.client.core.RETRIEVE_TX_STATUS_ATTEMPTS
import net.postchain.common.BlockchainRid
import net.postchain.gtv.GtvFactory.gtv
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.*
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import net.postchain.crypto.devtools.KeyPairHelper.pubKey
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtx.data.GTXDataBuilder
import net.postchain.crypto.devtools.KeyPairHelper

internal class ConcretePostchainClientTest {
    private var url = "http://localhost:7740"
    private lateinit var nodeResolver: PostchainNodeResolver
    private lateinit var httpResponse: CloseableHttpResponse
    private lateinit var httpClient: CloseableHttpClient
    private val brid = "EC03EDC6959E358B80D226D16A5BB6BC8EDE80EC17BD8BD0F21846C244AE7E8F"
    private val cryptoSystem = Secp256K1CryptoSystem()
    private val gtxdataBuilder = GTXDataBuilder(BlockchainRid.ZERO_RID, arrayOf(KeyPairHelper.pubKey(0)), cryptoSystem)

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
        // Just any operation
        gtxdataBuilder.addOperation("nop", arrayOf(gtv((0..100000).random().toLong())))
        gtxdataBuilder.finish()
        gtxdataBuilder.sign(cryptoSystem.buildSigMaker(KeyPairHelper.pubKey(0), KeyPairHelper.privKey(0)))

        // Execute
        client.postTransactionSync(gtxdataBuilder, ConfirmationLevel.UNVERIFIED)

        // Verify
        verify(httpClient, times(1)).execute(any<HttpPost>())
        verify(httpClient, times(numberExpected)).execute(any<HttpGet>())
    }

    @Test
    fun `Max number of attempts by default`() {
        driveTestCorrectNumberOfAttempts(
            ConcretePostchainClient(nodeResolver, BlockchainRid.buildFromHex(brid), null, httpClient = httpClient),
            // If I didn't pass a max value, it defaults to RETRIEVE_TX_STATUS_ATTEMPTS = 20
            numberExpected = RETRIEVE_TX_STATUS_ATTEMPTS)
    }

    @Test
    fun `Max number of attempts parameterized`() {
        driveTestCorrectNumberOfAttempts(
            ConcretePostchainClient(nodeResolver, BlockchainRid.buildFromHex(brid), null, 10, httpClient),
            // If I pass a custom max value, verify it uses it
            numberExpected = 10)
    }
}
