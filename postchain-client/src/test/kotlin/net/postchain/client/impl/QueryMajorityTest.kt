package net.postchain.client.impl

import net.postchain.client.DeterministicEndpointPool
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.BlockDetail
import net.postchain.client.core.TransactionResult
import net.postchain.client.core.TxRid
import net.postchain.client.exception.ClientError
import net.postchain.client.exception.NodesDisagree
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.tx.TransactionStatus
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder.encodeGtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import org.http4k.client.AsyncHttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class QueryMajorityTest {
    private val urls = listOf("http://localhost:1", "http://localhost:2", "http://localhost:3", "http://localhost:4")
    private val bcRid = "EC03EDC6959E358B80D226D16A5BB6BC8EDE80EC17BD8BD0F21846C244AE7E8F"

    private var requestCounter = 0

    @BeforeEach
    fun setup() {
        requestCounter = 0
    }

    @Test
    fun `nodes agree`() {
        val queryResponse: Gtv = makeQuery(object : AsyncHttpHandler {
            override fun invoke(request: Request, fn: (Response) -> Unit) {
                requestCounter++
                fn(Response(Status.OK).body(encodeGtv(gtv("query_response")).inputStream()))
            }
        })
        assertEquals("query_response", queryResponse.asString())
        assertEquals(4, requestCounter)
    }

    @Test
    fun `all nodes disagree`() {
        assertThrows<NodesDisagree> {
            makeQuery(object : AsyncHttpHandler {
                override fun invoke(request: Request, fn: (Response) -> Unit) {
                    requestCounter++
                    fn(Response(Status.OK).body(encodeGtv(gtv("query_response ${request.uri.port}")).inputStream()))
                }
            })
        }
        assertEquals(4, requestCounter)
    }

    @Test
    fun `one node disagree`() {
        val queryResponse: Gtv = makeQuery(object : AsyncHttpHandler {
            override fun invoke(request: Request, fn: (Response) -> Unit) {
                requestCounter++
                fn(Response(Status.OK).body(encodeGtv(gtv(
                        if (request.uri.port == 1) "bogus_response" else "query_response"
                )).inputStream()))
            }
        })
        assertEquals("query_response", queryResponse.asString())
        assertEquals(4, requestCounter)
    }

    @Test
    fun `first node fails and rest disagree`() {
        assertThrows<NodesDisagree> {
            makeQuery(object : AsyncHttpHandler {
                override fun invoke(request: Request, fn: (Response) -> Unit) {
                    requestCounter++
                    fn(if ((request.uri.port ?: 0) > 1)
                        Response(Status.OK).body(encodeGtv(gtv("query_response ${request.uri.port}")).inputStream())
                    else
                        Response(Status.BAD_REQUEST).body(encodeGtv(gtv("the error")).inputStream()))
                }
            })
        }
        assertEquals(4, requestCounter)
    }

    @Test
    fun `first node fails and rest agree`() {
        assertEquals("query_response", nodesFail(1).asString())
        assertEquals(4, requestCounter)
    }

    @Test
    fun `two nodes fail`() {
        assertThrows<ClientError> { nodesFail(2) }
        assertEquals(4, requestCounter)
    }

    @Test
    fun `three nodes fail`() {
        assertThrows<ClientError> { nodesFail(3) }
        assertEquals(4, requestCounter)
    }

    @Test
    fun `all nodes fail`() {
        assertThrows<ClientError> { nodesFail(4) }
        assertEquals(4, requestCounter)
    }

    private fun nodesFail(failingNodes: Int): Gtv =
            makeQuery(object : AsyncHttpHandler {
                override fun invoke(request: Request, fn: (Response) -> Unit) {
                    requestCounter++
                    fn(if ((request.uri.port ?: 0) > failingNodes)
                        Response(Status.OK).body(encodeGtv(gtv("query_response")).inputStream())
                    else
                        Response(Status.BAD_REQUEST).body(encodeGtv(gtv("the error")).inputStream()))
                }
            })

    private fun makeQuery(asyncHttpHandler: AsyncHttpHandler): Gtv =
            PostchainClientImpl(
                    PostchainClientConfig(
                            BlockchainRid.buildFromHex(bcRid),
                            DeterministicEndpointPool(urls),
                            requestStrategy = QueryMajorityRequestStrategyFactory(asyncHttpHandler))
            ).query("test_query", gtv("arg"))

    @Test
    fun `blockAtHeight found`() {
        val someBlock: BlockDetail? = PostchainClientImpl(PostchainClientConfig(
                BlockchainRid.buildFromHex(bcRid),
                DeterministicEndpointPool(urls),
                requestStrategy = QueryMajorityRequestStrategyFactory(object : AsyncHttpHandler {
                    override fun invoke(request: Request, fn: (Response) -> Unit) {
                        requestCounter++
                        assertEquals("application/octet-stream", request.header("Accept"))
                        fn(Response(Status.OK).body(blockDetail(1).inputStream()))
                    }
                }))).blockAtHeight(1L)
        assertEquals(1L, someBlock!!.height)
        assertEquals(4, requestCounter)
    }

    @Test
    fun `blockAtHeight not found`() {
        val someBlock: BlockDetail? = PostchainClientImpl(PostchainClientConfig(
                BlockchainRid.buildFromHex(bcRid),
                DeterministicEndpointPool(urls),
                requestStrategy = QueryMajorityRequestStrategyFactory(object : AsyncHttpHandler {
                    override fun invoke(request: Request, fn: (Response) -> Unit) {
                        requestCounter++
                        assertEquals("application/octet-stream", request.header("Accept"))
                        fn(Response(Status.OK).body(encodeGtv(GtvNull).inputStream()))
                    }
                }))).blockAtHeight(1L)
        assertNull(someBlock)
        assertEquals(4, requestCounter)
    }

    @Test
    fun `blockAtHeight disagree`() {
        assertThrows<NodesDisagree> {
            PostchainClientImpl(PostchainClientConfig(
                    BlockchainRid.buildFromHex(bcRid),
                    DeterministicEndpointPool(urls),
                    requestStrategy = QueryMajorityRequestStrategyFactory(object : AsyncHttpHandler {
                        override fun invoke(request: Request, fn: (Response) -> Unit) {
                            requestCounter++
                            assertEquals("application/octet-stream", request.header("Accept"))
                            fn(Response(Status.OK).body(blockDetail((request.uri.port ?: 0).toLong()).inputStream()))
                        }
                    }))).blockAtHeight(1L)
        }
        assertEquals(4, requestCounter)
    }

    @Test
    fun `blockAtHeight disagree found or not found`() {
        assertThrows<NodesDisagree> {
            PostchainClientImpl(PostchainClientConfig(
                    BlockchainRid.buildFromHex(bcRid),
                    DeterministicEndpointPool(urls),
                    requestStrategy = QueryMajorityRequestStrategyFactory(object : AsyncHttpHandler {
                        override fun invoke(request: Request, fn: (Response) -> Unit) {
                            requestCounter++
                            assertEquals("application/octet-stream", request.header("Accept"))
                            fn(if ((request.uri.port ?: 0) > 2)
                                Response(Status.OK).body(encodeGtv(GtvNull).inputStream())
                            else
                                Response(Status.OK).body(blockDetail(1).inputStream()))
                        }
                    }))).blockAtHeight(1L)
        }
        assertEquals(4, requestCounter)
    }

    @Test
    fun `txStatus agree`() {
        val txStatus: TransactionResult = PostchainClientImpl(PostchainClientConfig(
                BlockchainRid.buildFromHex(bcRid),
                DeterministicEndpointPool(urls),
                requestStrategy = QueryMajorityRequestStrategyFactory(object : AsyncHttpHandler {
                    override fun invoke(request: Request, fn: (Response) -> Unit) {
                        requestCounter++
                        fn(Response(Status.OK).body("""{"status":"CONFIRMED"}"""))
                    }
                }))).checkTxStatus(TxRid("62F71D71BA63D03FA0C6741DE22B116A3A8022893E7977DDC2A9CD981BBADE29"))
        assertEquals(TransactionStatus.CONFIRMED, txStatus.status)
        assertEquals(4, requestCounter)
    }

    private fun blockDetail(timestamp: Long) = encodeGtv(gtv(mapOf(
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
            "timestamp" to gtv(timestamp)
    )))
}
