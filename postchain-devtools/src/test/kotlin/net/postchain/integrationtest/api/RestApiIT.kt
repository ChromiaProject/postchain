// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.integrationtest.api

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.isContentEqualTo
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import net.postchain.common.BlockchainRid
import net.postchain.common.data.Hash
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.configurations.GTXTestModule
import net.postchain.core.EContext
import net.postchain.crypto.KeyPair
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.RestTools
import net.postchain.devtools.testinfra.TestOneOpGtxTransaction
import net.postchain.devtools.utils.configuration.SystemSetup
import net.postchain.devtools.utils.configuration.system.SystemSetupFactory
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvFileReader
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtx.GTXTransactionFactory
import net.postchain.gtx.GtxBuilder
import net.postchain.gtx.SimpleGTXModule
import net.postchain.integrationtest.JsonTools
import net.postchain.integrationtest.JsonTools.jsonAsMap
import net.postchain.integrationtest.reconfiguration.TogglableFaultyGtxModule
import org.awaitility.Awaitility.await
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.core.IsEqual
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class RestApiIT : IntegrationTestSetup() {

    private val gson = JsonTools.buildGson()
    private var txHashHex = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

    private val gtxTestModule = GTXTestModule()
    private val chainIid = 1

    private fun doSystemSetup(nodeCount: Int, bcConfFileName: String): SystemSetup {
        configOverrides.setProperty("testpeerinfos", createPeerInfos(nodeCount))
        val bcConfFileMap = mapOf(chainIid to bcConfFileName)
        val sysSetup = SystemSetupFactory.buildSystemSetup(bcConfFileMap)
        assertEquals(nodeCount, sysSetup.nodeMap.size, "We didn't get the nodes we expected, check BC config file")
        sysSetup.needRestApi = true // NOTE!! This is important in this test!!

        createNodesFromSystemSetup(sysSetup)
        return sysSetup
    }

    @Test
    fun testMixedAPICalls() {
        val nodeCount = 4
        val sysSetup = doSystemSetup(nodeCount, "/net/postchain/devtools/api/blockchain_config.xml")
        val blockchainRIDBytes = sysSetup.blockchainMap[chainIid]!!.rid
        val blockchainRID = blockchainRIDBytes.toHex()

        testStatusGet("/tx/$blockchainRID/$txHashHex", 404)
        testStatusGet("/tx/$blockchainRID/$txHashHex/status", 200) {
            assertEquals(
                    jsonAsMap(gson, "{\"status\"=\"unknown\"}"),
                    jsonAsMap(gson, it))
        }

        val factory = GTXTransactionFactory(blockchainRIDBytes, gtxTestModule, cryptoSystem)

        val blockHeight = 0 // If we set it to zero the node with index 0 will get the post
        val tx = postGtxTransaction(factory, 1, blockHeight, nodeCount, blockchainRIDBytes)

        awaitConfirmed(blockchainRID, tx.getRID())

        // Note: here we use the "iid_1" method instead of BC RID
        testStatusGet("/tx/iid_$chainIid/${tx.getRID().toHex()}/status", 200) {
            assertEquals(
                    jsonAsMap(gson, "{\"status\"=\"confirmed\"}"),
                    jsonAsMap(gson, it))
        }
    }

    @Test
    fun testDirectQueryApi() {
        val nodeCount = 1

        val sysSetup = doSystemSetup(nodeCount, "/net/postchain/devtools/api/blockchain_config_dquery.xml")
        val blockchainRIDBytes = sysSetup.blockchainMap[chainIid]!!.rid
        val blockchainRID = blockchainRIDBytes.toHex()

        buildBlockAndCommit(nodes[0])

        // /get_picture
        val expect1 = "abcd"
        val byteArray = given().port(nodes[0].getRestApiHttpPort())
                .get("/dquery/$blockchainRID?type=get_picture&id=1234")
                .then()
                .statusCode(200)
                .extract().asByteArray()
        assertEquals(expect1, String(byteArray))

        // /get_front_page
        val expect2 = "<h1>it works!</h1>"
        val text = given().port(nodes[0].getRestApiHttpPort())
                .get("/dquery/$blockchainRID?type=get_front_page&id=1234")
                .then()
                .statusCode(200)
                .extract().asString()
        assertEquals(expect2, text)
    }

    @Test
    fun testGetQuery() {
        val nodesCount = 1
        configOverrides.setProperty("testpeerinfos", createPeerInfos(nodesCount))
        configOverrides.setProperty("api.port", 0)
        val nodes = createNodes(nodesCount, "/net/postchain/devtools/api/blockchain_config_getquery.xml")
        val blockchainRIDBytes = nodes[0].getBlockchainRid(1L)!! // Just take first chain from first node.
        val blockchainRID = blockchainRIDBytes.toHex()

        buildBlockAndCommit(nodes[0])

        // returns `num * num`
        val num = 1000
        val expect1 = "1000000"
        var returnVal = given().port(nodes[0].getRestApiHttpPort())
                .get("/query/$blockchainRID?type=test_query&i=$num&flag=true")
                .then()
                .statusCode(200)
                .extract().asString()
        assertEquals(expect1, returnVal)

        // returns `num`
        val expect2 = "1000"
        returnVal = given().port(nodes[0].getRestApiHttpPort())
                .get("/query/$blockchainRID?type=test_query&i=$num&flag=false")
                .then()
                .statusCode(200)
                .extract().asString()
        assertEquals(expect2, returnVal)
    }

    @Test
    fun testRejectedTransactionWithReason() {
        val nodesCount = 1
        val sysSetup = doSystemSetup(nodesCount, "/net/postchain/devtools/api/blockchain_config_rejected.xml")
        val bcRid = sysSetup.blockchainMap[chainIid]!!.rid
        val blockchainRID = bcRid.toHex()

        val builder = GtxBuilder(bcRid, listOf(KeyPairHelper.pubKey(0)), cryptoSystem)
                .addOperation("gtx_test", gtv(1L), gtv("rejectMe"))
                .finish()
                .sign(cryptoSystem.buildSigMaker(KeyPair(KeyPairHelper.pubKey(0), KeyPairHelper.privKey(0))))
                .buildGtx()

        // post transaction
        testStatusPost(
                0,
                "/tx/$blockchainRID",
                "{\"tx\": \"${builder.encode().toHex()}\"}",
                200)

        // Asserting
        val txRidHex = builder.calculateTxRid(GtvMerkleHashCalculator(cryptoSystem)).toHex()
        val expected = """
            {
                "status": "rejected",
                "rejectReason": "You were asking for it"
            }
        """.trimIndent()

        await().untilAsserted {
            val body = given().port(nodes[0].getRestApiHttpPort())
                    .get("/tx/$blockchainRID/$txRidHex/status")
                    .then()
                    .statusCode(200)
                    .extract().body().asString()

            JSONAssert.assertEquals(expected, body, JSONCompareMode.STRICT)
        }
    }

    /**
     * Test that we get the overloaded error.
     *
     * The reason we don't do the 503 (Overloaded) test as a unit test is that the setup requires a few more classes.
     */
    @Test
    fun testTransactionQueueFullWithReason() {
        val nodesCount = 1

        val sysSetup = doSystemSetup(nodesCount, "/net/postchain/devtools/api/blockchain_config_tx_queue_size.xml")
        val blockchainRIDBytes = sysSetup.blockchainMap[chainIid]!!.rid
        val blockchainRID = blockchainRIDBytes.toHex()

        // ---- To post a TX ---
        val factory = GTXTransactionFactory(blockchainRIDBytes, gtxTestModule, cryptoSystem)

        val blockHeight = 0
        var currentId = 0

        // ----- TX = 1 ------
        val tx1 = TestOneOpGtxTransaction(factory, currentId)
        val strHexData1 = tx1.getRawData().toHex()
        //println("Sending TX: $strHexData:")

        testStatusPost(
                blockHeight,
                "/tx/${blockchainRID}",
                "{\"tx\": \"$strHexData1\"}",
                200
        )

        // ----- TX = 2 ------
        currentId++
        val tx2 = TestOneOpGtxTransaction(factory, currentId)
        val strHexData2 = tx2.getRawData().toHex()
        //println("Sending TX: $strHexData:")

        testStatusPost(
                blockHeight,
                "/tx/${blockchainRID}",
                "{\"tx\": \"$strHexData2\"}",
                503 // This should fail since we set the TX queue max size 1
        )
    }

    @Test
    fun testGetBlockchainConfiguration() {
        val nodeCount = 4
        val blockChainFile = "/net/postchain/devtools/api/blockchain_config.xml"
        val sysSetup = doSystemSetup(nodeCount, blockChainFile)
        val blockchainRIDBytes = sysSetup.blockchainMap[chainIid]!!.rid
        val blockchainRID = blockchainRIDBytes.toHex()
        val config = GtvEncoder.encodeGtv(GtvFileReader.readFile(Paths.get(javaClass.getResource(blockChainFile)!!.toURI()).toFile()))
        val byteArray = given().port(nodes[0].getRestApiHttpPort())
                .header("Accept", ContentType.BINARY)
                .get("/config/$blockchainRID?height=0")
                .then()
                .statusCode(200)
                .extract().asByteArray()
        assertThat(byteArray).isContentEqualTo(config)
    }

    @Test
    fun testGetBlockchainConfigurationWithInvalidHeight() {
        val nodeCount = 4
        val blockChainFile = "/net/postchain/devtools/api/blockchain_config.xml"
        val sysSetup = doSystemSetup(nodeCount, blockChainFile)
        val blockchainRIDBytes = sysSetup.blockchainMap[chainIid]!!.rid
        val blockchainRID = blockchainRIDBytes.toHex()
        given().port(nodes[0].getRestApiHttpPort())
                .header("Accept", ContentType.BINARY)
                .get("/config/$blockchainRID?height=-42")
                .then()
                .statusCode(400)
    }

    @Test
    fun testValidateBlockchainConfigurationWithSigners() {
        val blockChainFile = "/net/postchain/devtools/api/blockchain_config_1.xml"
        val sysSetup = doSystemSetup(1, blockChainFile)
        val blockchainRIDBytes = sysSetup.blockchainMap[chainIid]!!.rid
        val blockchainRID = blockchainRIDBytes.toHex()
        val config = GtvEncoder.encodeGtv(GtvFileReader.readFile(Paths.get(javaClass.getResource(blockChainFile)!!.toURI()).toFile()))
        given().port(nodes[0].getRestApiHttpPort())
                .header("Content-Type", ContentType.BINARY)
                .body(config)
                .post("/config/$blockchainRID")
                .then()
                .statusCode(200)
    }

    @Test
    fun testValidateBlockchainConfigurationWithEmptySigners() {
        val blockChainFile = "/net/postchain/devtools/api/blockchain_config_1.xml"
        val blockChainFileToValidate = "/net/postchain/devtools/api/blockchain_config_empty_signers.xml"
        val sysSetup = doSystemSetup(1, blockChainFile)
        val blockchainRIDBytes = sysSetup.blockchainMap[chainIid]!!.rid
        val blockchainRID = blockchainRIDBytes.toHex()
        val config = GtvEncoder.encodeGtv(GtvFileReader.readFile(Paths.get(javaClass.getResource(blockChainFileToValidate)!!.toURI()).toFile()))
        given().port(nodes[0].getRestApiHttpPort())
                .header("Content-Type", ContentType.BINARY)
                .body(config)
                .post("/config/$blockchainRID")
                .then()
                .statusCode(200)
    }

    @Test
    fun testValidateBlockchainConfigurationWithoutSigners() {
        val blockChainFile = "/net/postchain/devtools/api/blockchain_config_1.xml"
        val blockChainFileToValidate = "/net/postchain/devtools/api/blockchain_config_no_signers.xml"
        val sysSetup = doSystemSetup(1, blockChainFile)
        val blockchainRIDBytes = sysSetup.blockchainMap[chainIid]!!.rid
        val blockchainRID = blockchainRIDBytes.toHex()
        val config = GtvEncoder.encodeGtv(GtvFileReader.readFile(Paths.get(javaClass.getResource(blockChainFileToValidate)!!.toURI()).toFile()))
        given().port(nodes[0].getRestApiHttpPort())
                .header("Content-Type", ContentType.BINARY)
                .body(config)
                .post("/config/$blockchainRID")
                .then()
                .statusCode(200)
    }

    @Test
    fun testInvalidBlockchainConfiguration() {
        val blockChainFile = "/net/postchain/devtools/api/blockchain_config_1.xml"
        val blockChainFileToValidate = "/net/postchain/devtools/api/blockchain_config_bad.xml"
        val sysSetup = doSystemSetup(1, blockChainFile)
        val blockchainRIDBytes = sysSetup.blockchainMap[chainIid]!!.rid
        val blockchainRID = blockchainRIDBytes.toHex()
        val config = GtvEncoder.encodeGtv(GtvFileReader.readFile(Paths.get(javaClass.getResource(blockChainFileToValidate)!!.toURI()).toFile()))
        given().port(nodes[0].getRestApiHttpPort())
                .header("Content-Type", ContentType.BINARY)
                .body(config)
                .post("/config/$blockchainRID")
                .then()
                .statusCode(400)
    }

    @Test
    fun testGetBlockchainNodeState() {
        val blockChainFile = "/net/postchain/devtools/api/blockchain_config_1.xml"
        val sysSetup = doSystemSetup(1, blockChainFile)
        val blockchainRIDBytes = sysSetup.blockchainMap[chainIid]!!.rid
        val blockchainRID = blockchainRIDBytes.toHex()
        given().port(nodes[0].getRestApiHttpPort())
                .get("/blockchain/$blockchainRID/nodestate")
                .then()
                .statusCode(200)
                .body(IsEqual.equalTo("{\"state\":\"RUNNING_VALIDATOR\"}"))
    }

    @Test
    fun `Get Transactions should return blocks and transactions in descending order`() {
        val nodeCount = 1
        val blockChainFile = "/net/postchain/devtools/api/blockchain_config_1.xml"
        val sysSetup = doSystemSetup(nodeCount, blockChainFile)
        val blockchainRIDBytes = sysSetup.blockchainMap[chainIid]!!.rid
        val blockchainRID = blockchainRIDBytes.toHex()
        val factory = GTXTransactionFactory(blockchainRIDBytes, gtxTestModule, cryptoSystem)
        val blocks = mutableListOf<List<TestOneOpGtxTransaction>>()
        val blockCount = 3
        val txPerBlockCount = 3

        // create blocks
        createBlocks(blockCount, txPerBlockCount, factory, nodeCount, blockchainRIDBytes, blocks)

        // get transactions
        val body = given().port(nodes[0].getRestApiHttpPort())
                .get("/transactions/$blockchainRID")
                .then()
                .statusCode(200)
                .extract().body().asString()

        // verify order of blocks and transactions
        val jsonArray = JsonParser.parseString(body) as JsonArray
        assertEquals(blockCount * txPerBlockCount, jsonArray.size())
        var itemInArray = 0
        for (blockHeight in blockCount - 1 downTo 0) {
            val transactions = blocks[blockHeight]
            for (txInBlock in txPerBlockCount - 1 downTo 0) {
                val txObject: JsonObject = jsonArray[itemInArray] as JsonObject
                assertEquals(txObject["blockHeight"].asInt, blockHeight)
                assertThat(transactions[txInBlock].getRID()).isContentEqualTo(txObject["txRID"].asString.hexStringToByteArray())
                itemInArray++
            }
        }
    }

    @Test
    fun `can get blocks and transactions even if chain is not live`() {
        val nodeCount = 1
        TogglableFaultyGtxModule.shouldFail = false
        val sysSetup = doSystemSetup(nodeCount, "/net/postchain/devtools/api/blockchain_config_faulty_1.xml")
        val blockchainRIDBytes = sysSetup.blockchainMap[chainIid]!!.rid
        val blockchainRID = blockchainRIDBytes.toHex()
        val factory = GTXTransactionFactory(blockchainRIDBytes, gtxTestModule, cryptoSystem)
        val blocks = mutableListOf<List<TestOneOpGtxTransaction>>()
        val blockCount = 3
        val txPerBlockCount = 3

        // create blocks
        createBlocks(blockCount, txPerBlockCount, factory, nodeCount, blockchainRIDBytes, blocks)

        TogglableFaultyGtxModule.shouldFail = true
        val faultyConfig = readBlockchainConfig(
                "/net/postchain/devtools/api/blockchain_config_faulty_2.xml"
        )
        nodes[0].addConfiguration(PostchainTestNode.DEFAULT_CHAIN_IID, 4, faultyConfig)
        buildBlock(PostchainTestNode.DEFAULT_CHAIN_IID, 3)
        await().untilAsserted {
            assertThat(nodes[0].getRestApiModel().live).isFalse()
        }

        // get transactions
        given().port(nodes[0].getRestApiHttpPort())
                .get("/transactions/$blockchainRID")
                .then()
                .statusCode(200)

        // get blocks
        given().port(nodes[0].getRestApiHttpPort())
                .get("/blocks/$blockchainRID")
                .then()
                .statusCode(200)
    }

    @Test
    fun testDuplicateTx() {
        val nodeCount = 4
        val sysSetup = doSystemSetup(nodeCount, "/net/postchain/devtools/api/blockchain_config.xml")
        val blockchainRIDBytes = sysSetup.blockchainMap[chainIid]!!.rid
        val blockchainRID = blockchainRIDBytes.toHex()

        val factory = GTXTransactionFactory(blockchainRIDBytes, gtxTestModule, cryptoSystem)
        val tx = TestOneOpGtxTransaction(factory, 1)

        testStatusPost(
                0,
                "/tx/${blockchainRIDBytes.toHex()}",
                "{\"tx\": \"${tx.getRawData().toHex()}\"}",
                200)
        awaitConfirmed(blockchainRID, tx.getRID())

        testStatusPost(
                0,
                "/tx/${blockchainRIDBytes.toHex()}",
                "{\"tx\": \"${tx.getRawData().toHex()}\"}",
                409)
    }

    @Test
    fun `debug API`() {
        val nodeCount = 1
        val blockChainFile = "/net/postchain/devtools/api/blockchain_config_1.xml"
        val sysSetup = doSystemSetup(nodeCount, blockChainFile)
        val blockchainRIDBytes = sysSetup.blockchainMap[chainIid]!!.rid
        val blockchainRID = blockchainRIDBytes.toHex()
        given().port(nodes[0].getDebugApiHttpPort())
                .header("Accept", ContentType.JSON)
                .get("/_debug")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("blockchain[0].brid", equalTo(blockchainRID))
    }

    @Test
    fun `REST API limit number of concurrent requests overall`() {
        val nodeCount = 1
        val blockChainFile = "/net/postchain/devtools/api/blockchain_config_blockable_1.xml"
        configOverrides.setProperty("api.request-concurrency", 2)
        val sysSetup = doSystemSetup(nodeCount, blockChainFile)
        val blockchainRIDBytes = sysSetup.blockchainMap[chainIid]!!.rid
        val blockchainRID = blockchainRIDBytes.toHex()

        // run 3 blockable queries
        val queries = (0..2).map {
            CompletableFuture.supplyAsync {
                given().port(nodes[0].getRestApiHttpPort())
                        .get("/query_gtv/$blockchainRID?type=blockable")
                        .then()
                        .extract().body()
            }
        }
        // Give the queries time to execute
        Thread.sleep(1000)

        // Release semaphore so all queries can complete
        BlockableQueryGtxModule.querySemaphore.release(queries.size)

        CompletableFuture.allOf(*queries.toTypedArray()).get(10, TimeUnit.SECONDS)
        val responses = queries.map { GtvDecoder.decodeGtv(it.get().asByteArray()).asArray() }
        val maxStart = responses.maxOf { it[0].asInteger() }
        val minEnd = responses.minOf { it[1].asInteger() }
        assertThat(maxStart).isGreaterThan(minEnd)
    }

    @Test
    fun `REST API limit number of concurrent requests per chain`() {
        val nodeCount = 1
        val blockChainFile = "/net/postchain/devtools/api/blockchain_config_blockable_1.xml"
        configOverrides.setProperty("api.request-concurrency", 4)
        configOverrides.setProperty("api.chain-request-concurrency", 2)
        val sysSetup = doSystemSetup(nodeCount, blockChainFile)
        val blockchainRIDBytes = sysSetup.blockchainMap[chainIid]!!.rid
        val blockchainRID = blockchainRIDBytes.toHex()

        // run 2 blockable queries
        val query1Status = CompletableFuture.supplyAsync {
            given().port(nodes[0].getRestApiHttpPort())
                    .get("/query/$blockchainRID?type=blockable")
                    .then()
                    .extract().statusCode()
        }
        val query2Status = CompletableFuture.supplyAsync {
            given().port(nodes[0].getRestApiHttpPort())
                    .get("/query/$blockchainRID?type=blockable")
                    .then()
                    .extract().statusCode()
        }

        await().untilAsserted {
            assertThat(BlockableQueryGtxModule.querySemaphore.queueLength).isEqualTo(2)
        }

        // Assert that we can't have 3 queries running at the same time for same BC
        given().port(nodes[0].getRestApiHttpPort())
                .get("/query/$blockchainRID?type=blockable")
                .then()
                .statusCode(503)

        // Release the two queries
        BlockableQueryGtxModule.querySemaphore.release(2)

        CompletableFuture.allOf(query1Status, query2Status).get(10, TimeUnit.SECONDS)
        assertThat(query1Status.get()).isEqualTo(200)
        assertThat(query2Status.get()).isEqualTo(200)
    }

    /**
     * Will create and post a transaction to the servers
     *
     * @return the posted transaction
     */
    private fun postGtxTransaction(
            factory: GTXTransactionFactory,
            currentId: Int,
            blockHeight: Int,
            nodeCount: Int,
            bcRid: BlockchainRid
    ): TestOneOpGtxTransaction {
        val tx = TestOneOpGtxTransaction(factory, currentId)
        val strHexData = tx.getRawData().toHex()
        //println("Sending TX: $strHexData:")
        testStatusPost(
                blockHeight % nodeCount,
                "/tx/${bcRid.toHex()}",
                "{\"tx\": \"$strHexData\"}",
                200)

        return tx
    }

    private fun awaitConfirmed(blockchainRID: String, txRid: Hash) {
        RestTools.awaitConfirmed(
                nodes[0].getRestApiHttpPort(),
                blockchainRID,
                txRid.toHex())
    }

    private fun testStatusGet(path: String, expectedStatus: Int, extraChecks: (responseBody: String) -> Unit = {}) {
        val response = given().port(nodes[0].getRestApiHttpPort())
                .get(path)
                .then()
                .statusCode(expectedStatus)
                .extract()

        extraChecks(response.body().asString())
    }

    private fun testStatusPost(toIndex: Int, path: String, body: String, expectedStatus: Int) {
        given().port(nodes[toIndex].getRestApiHttpPort())
                .body(body)
                .post(path)
                .then()
                .statusCode(expectedStatus)
    }

    private fun createBlocks(blockCount: Int, txPerBlockCount: Int, factory: GTXTransactionFactory, nodeCount: Int, blockchainRIDBytes: BlockchainRid, blocks: MutableList<List<TestOneOpGtxTransaction>>) {
        var currentId = 0
        for (blockHeight in 0 until blockCount) {
            val transactions = mutableListOf<TestOneOpGtxTransaction>()
            for (txInBlock in 0 until txPerBlockCount) {
                transactions.add(postGtxTransaction(factory, ++currentId, blockHeight, nodeCount, blockchainRIDBytes))
            }
            buildBlockAndCommit(nodes[0])
            blocks.add(transactions)
        }
    }
}

class BlockableQueryGtxModule : SimpleGTXModule<Unit>(Unit, mapOf(), mapOf(
        "blockable" to { _, _, _ ->
            val queryStart = System.currentTimeMillis()
            querySemaphore.acquire()
            val queryEnd = System.currentTimeMillis()
            gtv(listOf(gtv(queryStart), gtv(queryEnd)))
        }
)) {
    companion object {
        val querySemaphore = Semaphore(0)
    }

    override fun initializeDB(ctx: EContext) {}
}
