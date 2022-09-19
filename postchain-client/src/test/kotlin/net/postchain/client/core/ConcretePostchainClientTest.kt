package net.postchain.client.core

import net.postchain.client.config.FailOverConfig
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.config.STATUS_POLL_COUNT
import net.postchain.client.request.EndpointPool
import net.postchain.common.BlockchainRid
import org.http4k.client.AsyncHttpHandler
import org.http4k.core.Body
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.format.Gson.auto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals

internal class ConcretePostchainClientTest {
    private var url = "http://localhost:7740"
    private lateinit var nodeResolver: PostchainNodeResolver
    private lateinit var httpClient: AsyncHttpHandler
    private val brid = "EC03EDC6959E358B80D226D16A5BB6BC8EDE80EC17BD8BD0F21846C244AE7E8F"
    private var requestCounter = 0

    @BeforeEach
    fun setup() {
        nodeResolver = object : PostchainNodeResolver {
            override fun getNodeURL(blockchainRID: BlockchainRid) = url
        }

        httpClient = object : AsyncHttpHandler {
            override fun invoke(request: Request, fn: (Response) -> Unit) {
                requestCounter++
                fn(Response(Status.OK, ""))
            }

        }
    }

    fun driveTestCorrectNumberOfAttempts(client: ConcretePostchainClient, numberExpected: Int) {
        client.transactionBuilder()
            .addNop()
            .postSyncAwaitConfirmation()

        // Verify
        assertEquals(numberExpected + 1, requestCounter)
    }

    @Test
    fun `Max number of attempts by default`() {
        driveTestCorrectNumberOfAttempts(
            ConcretePostchainClient(PostchainClientConfig(
                BlockchainRid.buildFromHex(brid),
                EndpointPool.singleUrl(url),
                statusPollInterval = Duration.ZERO,
                failOverConfig = FailOverConfig(1)
            ), httpClient = httpClient),
            // If I didn't pass a max value, it defaults to RETRIEVE_TX_STATUS_ATTEMPTS = 20
            numberExpected = STATUS_POLL_COUNT)
    }

    @Test
    fun `Max number of attempts parameterized`() {
        driveTestCorrectNumberOfAttempts(
            ConcretePostchainClient(PostchainClientConfig(
                BlockchainRid.buildFromHex(brid),
                EndpointPool.singleUrl(url),
                statusPollCount = 10,
                statusPollInterval = Duration.ZERO,
                failOverConfig = FailOverConfig(1)
            ), httpClient = httpClient),
            // If I pass a custom max value, verify it uses it
            numberExpected = 10
        )
    }

    @Test
    fun `Query response without body should not crash` () {
        ConcretePostchainClient(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url)), httpClient = object : AsyncHttpHandler {
            override fun invoke(request: Request, fn: (Response) -> Unit) {
                fn(Response(Status.OK).body(Body.EMPTY))
            }
        }).query("foo")
    }

    @Test
    fun `Query error without body should not crash` () {
        ConcretePostchainClient(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url)), httpClient = object : AsyncHttpHandler {
            override fun invoke(request: Request, fn: (Response) -> Unit) {
                fn(Response(Status.BAD_REQUEST).body(Body.EMPTY))
            }
        }).query("foo")
    }

    @Test
    fun `Tx status retrieves underlying error` () {
        val result = ConcretePostchainClient(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url)), httpClient = object : AsyncHttpHandler {
            override fun invoke(request: Request, fn: (Response) -> Unit) {
                val txLens = Body.auto<TxStatus>().toLens()
                fn(txLens(TxStatus("rejected", "Message!"), Response(Status.BAD_REQUEST)))
            }
        }).checkTxStatus(TxRid("")).toCompletableFuture().join()
        assertEquals("Message!", result.rejectReason)
    }

    @Test
    fun `Await aborts if rejected` () {
        var nCalls = 0
        ConcretePostchainClient(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url)), httpClient = object : AsyncHttpHandler {
            override fun invoke(request: Request, fn: (Response) -> Unit) {
                nCalls++
                val txLens = Body.auto<TxStatus>().toLens()
                fn(txLens(TxStatus("rejected", "Message!"), Response(Status.BAD_REQUEST)))
            }
        }).awaitConfirmation(TxRid(""), 10, Duration.ZERO)
        assertEquals(1, nCalls)
    }
}
