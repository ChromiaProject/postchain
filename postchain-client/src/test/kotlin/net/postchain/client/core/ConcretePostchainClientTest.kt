package net.postchain.client.core

import net.postchain.client.config.FailOverConfig
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.config.STATUS_POLL_COUNT
import net.postchain.client.request.EndpointPool
import net.postchain.common.BlockchainRid
import net.postchain.gtv.Gtv
import org.http4k.client.AsyncHttpHandler
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.format.Gson.auto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun `Query response without body should not crash`() {
        ConcretePostchainClient(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url)), httpClient = object : AsyncHttpHandler {
            override fun invoke(request: Request, fn: (Response) -> Unit) {
                fn(Response(Status.OK).body(Body.EMPTY))
            }
        }).query("foo")
    }

    @Test
    fun `Query error without body should not crash`() {
        ConcretePostchainClient(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url)), httpClient = object : AsyncHttpHandler {
            override fun invoke(request: Request, fn: (Response) -> Unit) {
                fn(Response(Status.BAD_REQUEST).body(Body.EMPTY))
            }
        }).query("foo")
    }

    @Test
    fun `Tx status retrieves underlying error`() {
        val result = ConcretePostchainClient(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url)), httpClient = object : AsyncHttpHandler {
            override fun invoke(request: Request, fn: (Response) -> Unit) {
                val txLens = Body.auto<TxStatus>().toLens()
                fn(txLens(TxStatus("rejected", "Message!"), Response(Status.BAD_REQUEST)))
            }
        }).checkTxStatus(TxRid("")).toCompletableFuture().join()
        assertEquals("Message!", result.rejectReason)
    }

    @Test
    fun `Await aborts if rejected`() {
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

    @Test
    fun `Query by chainId instead of BlockchainRid`() {
        val config = PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url), queryByChainId = 10)
        assertQueryUrlEndsWith(config, "iid_10")
    }

    @Test
    fun `Query by blockchainRid instead of chainId`() {
        val config = PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url))
        assertQueryUrlEndsWith(config, brid)
    }

    @Test
    fun `blockAtHeight found`() {
        val someBlock: Gtv = ConcretePostchainClient(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url)), httpClient = object : AsyncHttpHandler {
            override fun invoke(request: Request, fn: (Response) -> Unit) {
                fn(Response(Status.OK).header("Content-Type", ContentType.APPLICATION_JSON.value).body("""
                {
                    "rid": "34ED10678AAE0414562340E8754A7CCD174B435B52C7F0A4E69470537AEE47E6",
                    "prevBlockRID": "5AF85874B9CCAC197AA739585449668BE15650C534E08705F6D60A6993FE906D",
                    "header": "023F9C7FBAFD92E53D7890A61B50B33EC0375FA424D60BD328AA2454408430C383",
                    "height": 1,
                    "transactions": [],
                    "witness": "03D8844CFC0CE7BECD33CDF49A9881364695C944E266E06356CDA11C2305EAB83A",
                    "timestamp": 0
                }                   
                """.trimIndent()))
            }
        }).blockAtHeight(1L).toCompletableFuture().join()
        assertEquals(1L, someBlock.asDict()["height"]!!.asInteger())
    }

    @Test
    fun `blockAtHeight missing`() {
        val noBlock: Gtv = ConcretePostchainClient(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url)), httpClient = object : AsyncHttpHandler {
            override fun invoke(request: Request, fn: (Response) -> Unit) {
                fn(Response(Status.OK).header("Content-Type", ContentType.APPLICATION_JSON.value).body("null"))
            }
        }).blockAtHeight(Long.MAX_VALUE).toCompletableFuture().join()
        assertTrue(noBlock.isNull())
    }

    private fun assertQueryUrlEndsWith(config: PostchainClientConfig, suffix: String) {
        ConcretePostchainClient(config, httpClient = object : AsyncHttpHandler {
            override fun invoke(request: Request, fn: (Response) -> Unit) {
                assert(request.uri.path.endsWith(suffix))
                fn(Response(Status.BAD_REQUEST).body(Body.EMPTY))
            }
        }).query("foo")
    }
}
