package net.postchain.client.impl

import com.google.gson.Gson
import net.postchain.client.config.FailOverConfig
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.config.STATUS_POLL_COUNT
import net.postchain.client.core.BlockDetail
import net.postchain.client.core.TxRid
import net.postchain.client.impl.PostchainClientImpl.CurrentBlockHeight
import net.postchain.client.impl.PostchainClientImpl.TxStatus
import net.postchain.client.request.EndpointPool
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.common.tx.TransactionStatus
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder.encodeGtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import org.apache.commons.io.input.InfiniteCircularInputStream
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.EOFException
import java.io.IOException
import java.time.Duration
import java.util.concurrent.CompletionException
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class PostchainClientImplTest {
    private var url = "http://localhost:7740"
    private lateinit var httpClient: HttpHandler
    private val brid = "EC03EDC6959E358B80D226D16A5BB6BC8EDE80EC17BD8BD0F21846C244AE7E8F"
    private var requestCounter = 0

    @BeforeEach
    fun setup() {
        httpClient = object : HttpHandler {
            override fun invoke(request: Request): Response {
                requestCounter++
                return Response(Status.OK, "")
            }
        }
    }

    private fun driveTestCorrectNumberOfAttempts(client: PostchainClientImpl, numberExpected: Int) {
        client.transactionBuilder()
                .addNop()
                .postAwaitConfirmation()

        // Verify
        assertEquals(numberExpected + 1, requestCounter)
    }

    @Test
    fun `Max number of attempts by default`() {
        driveTestCorrectNumberOfAttempts(
                PostchainClientImpl(PostchainClientConfig(
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
                PostchainClientImpl(PostchainClientConfig(
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
    fun `Post transaction should properly encode transaction`() {
        val client = PostchainClientImpl(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url)), httpClient = object : HttpHandler {
            override fun invoke(request: Request): Response {
                assertEquals(
                        """{"tx":"A5363034A52E302CA1220420EC03EDC6959E358B80D226D16A5BB6BC8EDE80EC17BD8BD0F21846C244AE7E8FA5023000A5023000A5023000"}""",
                        request.bodyString())
                return Response(Status.OK).body(Body.EMPTY)
            }
        })
        val txResult = client.transactionBuilder().finish().build().post()
        assertEquals(TransactionStatus.WAITING, txResult.status)
    }

    @Test
    fun `Query response without body should throw IOException`() {
        assertThrows<IOException> {
            PostchainClientImpl(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url)), httpClient = object : HttpHandler {
                override fun invoke(request: Request) = Response(Status.OK).body(Body.EMPTY)
            }).query("foo", gtv(mapOf()))
        }
    }

    @Test
    fun `Query error without body should throw IOException`() {
        assertThrows<IOException> {
            PostchainClientImpl(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url)), httpClient = object : HttpHandler {
                override fun invoke(request: Request) = Response(Status.BAD_REQUEST).body(Body.EMPTY)
            }).query("foo", gtv(mapOf()))
        }
    }

    @Test
    fun `Tx status retrieves underlying error`() {
        val result = PostchainClientImpl(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url)), httpClient = object : HttpHandler {
            override fun invoke(request: Request) =
                    Response(Status.BAD_REQUEST).body(Gson().toJson(TxStatus("rejected", "Message!")))
        }).checkTxStatus(TxRid(""))
        assertEquals("Message!", result.rejectReason)
    }

    @Test
    fun `too big tx status response is rejected`() {
        assertThrows(IOException::class.java) {
            try {
                PostchainClientImpl(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url), maxResponseSize = 1024), httpClient = object : HttpHandler {
                    override fun invoke(request: Request) =
                            Response(Status.OK).body(InfiniteCircularInputStream(ByteArray(16)))
                }).checkTxStatus(TxRid(""))
            } catch (e: CompletionException) {
                throw e.cause ?: e
            }
        }
    }

    @Test
    fun `Await aborts if rejected`() {
        var nCalls = 0
        PostchainClientImpl(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url)), httpClient = object : HttpHandler {
            override fun invoke(request: Request): Response {
                nCalls++
                return Response(Status.BAD_REQUEST).body(Gson().toJson(TxStatus("rejected", "Message!")))
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
        val someBlock: BlockDetail? = PostchainClientImpl(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url)), httpClient = object : HttpHandler {
            override fun invoke(request: Request): Response {
                assertEquals("application/octet-stream", request.header("Accept"))
                return Response(Status.OK).body(encodeGtv(gtv(mapOf(
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
                ))).inputStream())
            }
        }).blockAtHeight(1L)
        assertEquals(1L, someBlock!!.height)
        assertContentEquals("34ED10678AAE0414562340E8754A7CCD174B435B52C7F0A4E69470537AEE47E6".hexStringToByteArray(), someBlock.rid)
        assertContentEquals("62F71D71BA63D03FA0C6741DE22B116A3A8022893E7977DDC2A9CD981BBADE29".hexStringToByteArray(), someBlock.transactions[0].rid)
        assertNull(someBlock.transactions[0].data)
    }

    @Test
    fun `blockAtHeight missing`() {
        val noBlock: BlockDetail? = PostchainClientImpl(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url)), httpClient = object : HttpHandler {
            override fun invoke(request: Request) =
                    Response(Status.OK).body(encodeGtv(GtvNull).inputStream())
        }).blockAtHeight(Long.MAX_VALUE)
        assertTrue(noBlock == null)
    }

    @Test
    fun `raw query can be serialized correctly`() {
        val queryResponse: Gtv = PostchainClientImpl(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url)), httpClient = object : HttpHandler {
            override fun invoke(request: Request) =
                    Response(Status.OK).body(encodeGtv(gtv("query_response")).inputStream())
        }).query("test_query", gtv("arg"))
        assertEquals("query_response", queryResponse.asString())
    }

    @Test
    fun `too big response will be rejected`() {
        assertThrows(EOFException::class.java) {
            PostchainClientImpl(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url), maxResponseSize = 1024), httpClient = object : HttpHandler {
                override fun invoke(request: Request) =
                        Response(Status.OK).body(encodeGtv(gtv(ByteArray(2 * 1024))).inputStream())
            }).query("test_query", gtv("arg"))
        }
    }

    @Test
    fun `invalid GTV response will throw IOException`() {
        assertThrows(IOException::class.java) {
            PostchainClientImpl(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url), maxResponseSize = 1024), httpClient = object : HttpHandler {
                override fun invoke(request: Request) =
                        Response(Status.OK).body(ByteArray(100).inputStream())
            }).query("test_query", gtv("arg"))
        }
    }

    @Test
    fun `binary GTV error will be parsed`() {
        assertThrows(UserMistake::class.java, {
            PostchainClientImpl(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url), maxResponseSize = 1024), httpClient = object : HttpHandler {
                override fun invoke(request: Request) =
                        Response(Status.BAD_REQUEST).body(encodeGtv(gtv("the error")).inputStream())
            }).query("test_query", gtv("arg"))
        }, "Can not make a query: 400 the error")
    }

    @Test
    fun `current block height can be parsed`() {
        val currentBlockHeight: Long = PostchainClientImpl(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url)), httpClient = object : HttpHandler {
            override fun invoke(request: Request) =
                    Response(Status.OK).body(Gson().toJson(CurrentBlockHeight(0)))
        }).currentBlockHeight()
        assertEquals(0, currentBlockHeight)
    }

    @Test
    fun `too big block height response will be rejected`() {
        assertThrows(IOException::class.java) {
            PostchainClientImpl(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url), maxResponseSize = 1024), httpClient = object : HttpHandler {
                override fun invoke(request: Request) =
                        Response(Status.OK).body("""{"blockHeight":${" ".repeat(1024)}1}""")
            }).currentBlockHeight()
        }
    }

    @Test
    fun `Can handle empty error body`() {
        assertThrows(UserMistake::class.java) {
            PostchainClientImpl(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url), maxResponseSize = 1024), httpClient = object : HttpHandler {
                override fun invoke(request: Request) = Response(Status.BAD_REQUEST).body("")
            }).currentBlockHeight()
        }
    }

    @Test
    fun `Can handle too big error body`() {
        assertThrows(IOException::class.java) {
            PostchainClientImpl(PostchainClientConfig(BlockchainRid.buildFromHex(brid), EndpointPool.singleUrl(url), maxResponseSize = 1024), httpClient = object : HttpHandler {
                override fun invoke(request: Request) =
                        Response(Status.BAD_REQUEST).body("""{"error":"${"e".repeat(1024)}"}""")
            }).currentBlockHeight()
        }
    }

    private fun assertQueryUrlEndsWith(config: PostchainClientConfig, suffix: String) {
        PostchainClientImpl(config, httpClient = object : HttpHandler {
            override fun invoke(request: Request): Response {
                assert(request.uri.path.endsWith(suffix))
                return Response(Status.OK).body(encodeGtv(gtv("foobar")).inputStream())
            }
        }).query("foo", gtv(mapOf()))
    }
}
