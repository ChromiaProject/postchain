// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.client.impl

import net.postchain.api.rest.controller.Model
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.core.PostchainClient
import net.postchain.client.exception.NodesDisagree
import net.postchain.client.request.EndpointPool
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.common.BlockchainRid
import net.postchain.common.tx.TransactionStatus
import net.postchain.crypto.KeyPair
import net.postchain.crypto.PrivKey
import net.postchain.crypto.PubKey
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.utils.configuration.system.SystemSetupFactory
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.GtxQuery
import org.awaitility.Awaitility.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.any
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import java.time.Instant
import java.util.*
import org.junit.jupiter.api.Assertions.assertEquals

class PostchainClientTest : IntegrationTestSetup() {

    private val blockchainRIDStr = "ABABABABABABABABABABABABABABABABABABABABABABABABABABABABABABABAB"
    private val blockchainRID = BlockchainRid.buildFromHex(blockchainRIDStr)
    private val pubKey0 = PubKey(KeyPairHelper.pubKey(0))
    private val privKey0 = PrivKey(KeyPairHelper.privKey(0))
    private val sigMaker0 = cryptoSystem.buildSigMaker(KeyPair(pubKey0, privKey0))
    private val randomStr = "hello${Random().nextLong()}"

    private fun createTestNodes(nodesCount: Int, configFileName: String): Array<PostchainTestNode> {
        val sysSetup = SystemSetupFactory.buildSystemSetup(mapOf(1 to configFileName))
        sysSetup.needRestApi = true
        assertEquals(nodesCount, sysSetup.nodeMap.size)
        createNodesFromSystemSetup(sysSetup)
        return nodes.toTypedArray()
    }

    private fun createSignedNopTx(client: PostchainClient, bcRid: BlockchainRid): TransactionBuilder.PostableTransaction {
        return TransactionBuilder(client, bcRid, listOf(pubKey0.data), listOf(), cryptoSystem)
                .addOperation("gtx_test", gtv(1L), gtv(randomStr))
                .sign(sigMaker0)
    }

    private fun createPostChainClient(bcRid: BlockchainRid): PostchainClient {
        return PostchainClientProviderImpl().createClient(
                PostchainClientConfig(
                        bcRid,
                        EndpointPool.singleUrl("http://127.0.0.1:${nodes[0].getRestApiHttpPort()}"),
                        listOf(KeyPair(pubKey0, privKey0))
                ))
    }

    @Test
    fun makingAndPostingTransaction_UnsignedTransactionGiven_throws_Exception() {
        // Mock
        createTestNodes(1, "/net/postchain/devtools/api/blockchain_config_1.xml")
        val client = createPostChainClient(blockchainRID)
        assertThrows<IllegalArgumentException> {
            client.transactionBuilder().finish().build()
        }

        // When
        //assertEquals(TransactionStatus.REJECTED, txBuilder.postSync().status)
    }

    @Test
    fun makingAndPostingTransaction_SignedTransactionGiven_PostsSuccessfully() {
        // Mock
        createTestNodes(1, "/net/postchain/devtools/api/blockchain_config_1.xml")
        val client = spy(createPostChainClient(blockchainRID))
        val txBuilder = client.transactionBuilder()

        txBuilder.addOperation("nop")
        txBuilder.addOperation("nop", gtv(Instant.now().toEpochMilli()))
        val tx = txBuilder.sign(sigMaker0)

        // When
        tx.post()

        // Then
        verify(client).postTransaction(any())
    }

    @Test
    fun testPostTransactionApiConfirmLevelNoWait() {
        createTestNodes(1, "/net/postchain/devtools/api/blockchain_config_1.xml")
        val blockchainRid = systemSetup.blockchainMap[1]!!.rid
        val client = createPostChainClient(blockchainRid)
        val builder = createSignedNopTx(client, blockchainRid)
        val result = builder.post()
        assertEquals(TransactionStatus.WAITING, result.status)
    }

    @Test
    fun testPostTransactionApiConfirmLevelUnverified() {
        createTestNodes(4, "/net/postchain/devtools/api/blockchain_config.xml")
        val blockchainRid = systemSetup.blockchainMap[1]!!.rid
        val client = createPostChainClient(blockchainRid)
        val builder = createSignedNopTx(client, blockchainRid)
        val result = builder.postAwaitConfirmation()
        assertEquals(TransactionStatus.CONFIRMED, result.status)
    }

    @Test
    fun testQueryGtxClientApi() {
        createTestNodes(4, "/net/postchain/devtools/api/blockchain_config.xml")
        val blockchainRid = systemSetup.blockchainMap[1]!!.rid
        val client = createPostChainClient(blockchainRid)
        val builder = createSignedNopTx(client, blockchainRid)
        val result = builder.postAwaitConfirmation()
        val gtv = gtv("txRID" to gtv(result.txRid.rid))

        await().untilCallTo {
            client.query("gtx_test_get_value", gtv)
        } matches { resp ->
            resp?.asString() == randomStr
        }
    }

    @ParameterizedTest(name = "majority query with {0} disagreeing nodes should work {1}")
    @CsvSource(
            "0,true",
            "1,true",
            "2,false"
    )
    fun testQueryMajority(disagreeingNodes: Int, shouldWork: Boolean) {
        createTestNodes(4, "/net/postchain/devtools/api/blockchain_config.xml")
        val blockchainRid = systemSetup.blockchainMap[1]!!.rid
        val urls = nodes.map { "http://127.0.0.1:${it.getRestApiHttpPort()}" }
        val client = PostchainClientProviderImpl().createClient(
                PostchainClientConfig(
                        blockchainRid,
                        EndpointPool.default(urls),
                        listOf(KeyPair(pubKey0, privKey0)),
                        requestStrategy = QueryMajorityRequestStrategyFactory()
                ))
        val builder = createSignedNopTx(client, blockchainRid)
        val result = builder.postAwaitConfirmation()
        val gtv = gtv("txRID" to gtv(result.txRid.rid))

        for (n in 0 until disagreeingNodes) {
            nodes[n].overrideRestApiModel(MockModel(nodes[n].getRestApiModel()))
        }

        if (shouldWork) {
            assertEquals(randomStr, client.query("gtx_test_get_value", gtv).asString())
        } else {
            assertThrows<NodesDisagree> {
                client.query("gtx_test_get_value", gtv)
            }
        }
    }

    class MockModel(delegate: Model) : Model by delegate {
        override fun query(query: GtxQuery): Gtv = gtv("bogus")
    }
}
