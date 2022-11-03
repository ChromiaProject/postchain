package net.postchain.client.core

import net.postchain.client.config.FailOverConfig
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.config.STATUS_POLL_COUNT
import net.postchain.client.request.EndpointPool
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import org.http4k.client.AsyncHttpHandler
import org.http4k.core.Body
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.Gson.auto
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.EOFException
import java.time.Duration
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ConcretePostchainClientTest {
    private var url = "http://localhost:7740"
    private lateinit var nodeResolver: PostchainNodeResolver
    private lateinit var httpClient: AsyncHttpHandler
    private val brid = "EC03EDC6959E358B80D226D16A5BB6BC8EDE80EC17BD8BD0F21846C244AE7E8F"
    private var requestCounter = 0

    val txStatusLens = Body.auto<TxStatus>().toLens()

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
                fn(Response(Status.BAD_REQUEST).with(txStatusLens of TxStatus("rejected", "Message!")))
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
                fn(Response(Status.BAD_REQUEST).with(txStatusLens of TxStatus("rejected", "Message!")))
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
        val someBlock: BlockDetail? = ConcretePostchainClient(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url)), httpClient = object : AsyncHttpHandler {
            override fun invoke(request: Request, fn: (Response) -> Unit) {
                fn(Response(Status.OK).body(GtvEncoder.encodeGtv(gtv(mapOf(
                        "rid" to gtv("34ED10678AAE0414562340E8754A7CCD174B435B52C7F0A4E69470537AEE47E6".hexStringToByteArray()),
                        "prevBlockRID" to gtv("5AF85874B9CCAC197AA739585449668BE15650C534E08705F6D60A6993FE906D".hexStringToByteArray()),
                        "header" to gtv("023F9C7FBAFD92E53D7890A61B50B33EC0375FA424D60BD328AA2454408430C383".hexStringToByteArray()),
                        "height" to gtv(1),
                        "transactions" to gtv(listOf(gtv(mapOf(
                                "rid" to gtv("62F71D71BA63D03FA0C6741DE22B116A3A8022893E7977DDC2A9CD981BBADE29".hexStringToByteArray()),
                                "hash" to gtv("F537E4B8224F4BC84DB37AD3E4F898A3A9127D2E86C25213508F7236016E58B9".hexStringToByteArray()),
                                "data" to GtvNull
                        )))),
                        "witness" to gtv("03D8844CFC0CE7BECD33CDF49A9881364695C944E266E06356CDA11C2305EAB83A".hexStringToByteArray()),
                        "timestamp" to gtv(0)
                ))).inputStream()))
            }
        }).blockAtHeight(1L).toCompletableFuture().join()
        assertEquals(1L, someBlock!!.height)
        assertContentEquals("34ED10678AAE0414562340E8754A7CCD174B435B52C7F0A4E69470537AEE47E6".hexStringToByteArray(), someBlock.rid)
        assertContentEquals("62F71D71BA63D03FA0C6741DE22B116A3A8022893E7977DDC2A9CD981BBADE29".hexStringToByteArray(), someBlock.transactions[0].rid)
        assertNull(someBlock.transactions[0].data)
    }

    @Test
    fun `blockAtHeight missing`() {
        val noBlock: BlockDetail? = ConcretePostchainClient(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url)), httpClient = object : AsyncHttpHandler {
            override fun invoke(request: Request, fn: (Response) -> Unit) {
                fn(Response(Status.OK).body(GtvEncoder.encodeGtv(GtvNull).inputStream()))
            }
        }).blockAtHeight(Long.MAX_VALUE).toCompletableFuture().join()
        assertTrue(noBlock == null)
    }

    @Test
    fun `raw query can be serialized correctly`() {
        val queryResponse: Gtv = ConcretePostchainClient(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url)), httpClient = object : AsyncHttpHandler {
            override fun invoke(request: Request, fn: (Response) -> Unit) {
                fn(Response(Status.OK).body(GtvEncoder.encodeGtv(gtv("query_response")).inputStream()))
            }
        }).querySync("test_query", gtv("arg"))
        assertEquals("query_response", queryResponse.asString())
    }

    @Test
    fun `too big response will be rejected`() {
        assertThrows(EOFException::class.java) {
            ConcretePostchainClient(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url), maxResponseSize = 1024), httpClient = object : AsyncHttpHandler {
                override fun invoke(request: Request, fn: (Response) -> Unit) {
                    fn(Response(Status.OK).body(GtvEncoder.encodeGtv(gtv(ByteArray(2 * 1024))).inputStream()))
                }
            }).querySync("test_query", gtv("arg"))
        }
    }

    @Test
    fun `binary GTV error will be parsed`() {
        assertThrows(UserMistake::class.java, {
            ConcretePostchainClient(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url), maxResponseSize = 1024), httpClient = object : AsyncHttpHandler {
                override fun invoke(request: Request, fn: (Response) -> Unit) {
                    fn(Response(Status.BAD_REQUEST).body(GtvEncoder.encodeGtv(gtv("the error")).inputStream()))
                }
            }).querySync("test_query", gtv("arg"))
        }, "Can not make a query: 400 the error")
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
