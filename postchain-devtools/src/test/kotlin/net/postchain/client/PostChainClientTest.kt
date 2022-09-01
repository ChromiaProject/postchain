// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.client


import net.postchain.client.core.DefaultSigner
import net.postchain.client.core.PostchainClient
import net.postchain.client.core.PostchainClientFactory
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.tx.TransactionStatus
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.utils.configuration.system.SystemSetupFactory
import net.postchain.gtv.GtvFactory.gtv
import org.awaitility.Awaitility.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals

class PostChainClientTest : IntegrationTestSetup() {

    private val blockchainRIDStr = "ABABABABABABABABABABABABABABABABABABABABABABABABABABABABABABABAB"
    private val blockchainRID = BlockchainRid.buildFromHex(blockchainRIDStr)
    private val pubKey0 = KeyPairHelper.pubKey(0)
    private val privKey0 = KeyPairHelper.privKey(0)
    private val sigMaker0 = cryptoSystem.buildSigMaker(pubKey0, privKey0)
    private val defaultSigner = DefaultSigner(sigMaker0, pubKey0)
    private val randomStr = "hello${Random().nextLong()}"

    private fun createTestNodes(nodesCount: Int, configFileName: String): Array<PostchainTestNode> {
        val sysSetup = SystemSetupFactory.buildSystemSetup(mapOf(1 to configFileName))
        sysSetup.needRestApi = true
        assertEquals(nodesCount, sysSetup.nodeMap.size)
        createNodesFromSystemSetup(sysSetup)
        return nodes.toTypedArray()
    }

    private fun createSignedNopTx(client: PostchainClient, bcRid: BlockchainRid): TransactionBuilder.PostableTransaction {
        return TransactionBuilder(client, bcRid, listOf(pubKey0), cryptoSystem)
            .addOperation("gtx_test", gtv(1L), gtv(randomStr))
            .sign(sigMaker0)
    }

    private fun createPostChainClient(bcRid: BlockchainRid): PostchainClient {
        val resolver = PostchainClientFactory.makeSimpleNodeResolver("http://127.0.0.1:${nodes[0].getRestApiHttpPort()}")
        return PostchainClientFactory.getClient(resolver, bcRid, defaultSigner)
    }

    @Test
    fun makingAndPostingTransaction_SignedTransactionGiven_PostsSuccessfully() {
        // Mock
        val nodes = createTestNodes(1, "/net/postchain/devtools/api/blockchain_config_1.xml")
        val client = spy(createPostChainClient(blockchainRID))
        val txBuilder = createSignedNopTx(client, blockchainRID)

        // When
        txBuilder.post().thenAccept {
            // Then
            verify(client).postTransaction(any())
        }
    }

    @Test
    fun makingAndPostingSyncTransaction_UnsignedTransactionGiven_throws_Exception() {
        // Mock
        createTestNodes(1, "/net/postchain/devtools/api/blockchain_config_1.xml")
        val client = createPostChainClient(blockchainRID)
        assertThrows<UserMistake> {
            val txBuilder = client.makeTransaction().finish().build()
        }

        // When
        //assertEquals(TransactionStatus.REJECTED, txBuilder.postSync().status)
    }

    @Test
    fun makingAndPostingSyncTransaction_SignedTransactionGiven_PostsSuccessfully() {
        // Mock
        createTestNodes(1, "/net/postchain/devtools/api/blockchain_config_1.xml")
        val client = spy(createPostChainClient(blockchainRID))
        val txBuilder = client.makeTransaction()

        txBuilder.addOperation("nop")
        txBuilder.addOperation("nop", gtv(Instant.now().toEpochMilli()))
        val tx = txBuilder.sign(sigMaker0)

        // When
        tx.postSync()

        // Then
        verify(client).postTransactionSync(any())
    }

    @Test
    fun testPostTransactionApiConfirmLevelNoWait() {
        val nodes = createTestNodes(1, "/net/postchain/devtools/api/blockchain_config_1.xml")
        val blockchainRid = systemSetup.blockchainMap[1]!!.rid
        val client = createPostChainClient(blockchainRid)
        val builder = createSignedNopTx(client, blockchainRid)
        val result = builder.postSync()
        assertEquals(TransactionStatus.WAITING, result.status)
    }

    @Test
    fun testPostTransactionApiConfirmLevelNoWaitPromise() {
        val nodes = createTestNodes(1, "/net/postchain/devtools/api/blockchain_config_1.xml")
        val blockchainRid = systemSetup.blockchainMap[1]!!.rid
        val client = createPostChainClient(blockchainRid)
        val builder = createSignedNopTx(client, blockchainRid)

        await().untilCallTo {
            builder.post().toCompletableFuture().join()
        } matches { resp ->
            resp?.status == TransactionStatus.WAITING
        }
    }

    @Test
    fun testPostTransactionApiConfirmLevelUnverified() {
        val nodes = createTestNodes(3, "/net/postchain/devtools/api/blockchain_config.xml")
        val blockchainRid = systemSetup.blockchainMap[1]!!.rid
        val client = createPostChainClient(blockchainRid)
        val builder = createSignedNopTx(client, blockchainRid)
        val result = builder.postSyncAwaitConfirmation()
        assertEquals(TransactionStatus.CONFIRMED, result.status)
    }

    @Test
    fun testQueryGtxClientApiPromise() {
        val nodes = createTestNodes(3, "/net/postchain/devtools/api/blockchain_config.xml")
        val blockchainRid = systemSetup.blockchainMap[1]!!.rid
        val client = createPostChainClient(blockchainRid)
        val builder = createSignedNopTx(client, blockchainRid)
        val result = builder.postSyncAwaitConfirmation()
        val gtv = gtv("txRID" to gtv(result.txRid.rid))

        await().untilCallTo {
            client.query("gtx_test_get_value", gtv).toCompletableFuture().join()
        } matches { resp ->
            resp?.asString() == randomStr
        }
    }
}