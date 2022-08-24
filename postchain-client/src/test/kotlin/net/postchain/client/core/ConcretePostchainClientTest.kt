package net.postchain.client.cli

import net.postchain.client.core.ConfirmationLevel
import net.postchain.client.core.GTXTransactionBuilder
import net.postchain.client.core.PostchainClient
import net.postchain.client.core.ConcretePostchainClient
import net.postchain.client.core.PostchainClientProvider
import net.postchain.client.core.PostchainNodeResolver
import net.postchain.client.core.ConcretePostchainClientProvider
import net.postchain.client.core.DefaultSigner
import net.postchain.common.BlockchainRid
import net.postchain.gtv.GtvFactory.gtv
import org.junit.jupiter.api.Test
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

    fun driveTestCorrectNumberOfAttempts(numberPassed: Int?, numberExpected: Int) {
        val url = "http://localhost:7740"
        val nodeResolver = object : PostchainNodeResolver {
            override fun getNodeURL(blockchainRID: BlockchainRid) = url
        }

        val httpResponse: CloseableHttpResponse = mock {
            on { getCode() } doReturn 200
        }

        val httpClient: CloseableHttpClient = mock {
            on { execute(any()) } doReturn httpResponse
        }

        val brid = "EC03EDC6959E358B80D226D16A5BB6BC8EDE80EC17BD8BD0F21846C244AE7E8F"
        val client: ConcretePostchainClient
        if (null != numberPassed) {
            client = ConcretePostchainClient(nodeResolver, BlockchainRid.buildFromHex(brid),
            null, numberPassed, httpClient)
        } else {
            client = ConcretePostchainClient(nodeResolver, BlockchainRid.buildFromHex(brid),
            null, httpClient = httpClient)
        }

        val cryptoSystem = Secp256K1CryptoSystem()
        val gtxdataBuilder = GTXDataBuilder(BlockchainRid.ZERO_RID, arrayOf(KeyPairHelper.pubKey(0)), cryptoSystem)
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
    fun `Correct number of attempts by default`() {
        driveTestCorrectNumberOfAttempts(numberPassed = null, numberExpected = 20)
    }

    @Test
    fun `Correct number of attempts parameterized`() {
        driveTestCorrectNumberOfAttempts(numberPassed = 10, numberExpected = 10)
    }
}
