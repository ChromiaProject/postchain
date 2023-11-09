// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.integrationtest.api

import io.restassured.RestAssured.given
import net.postchain.base.BaseBlockHeader
import net.postchain.base.ConfirmationProof
import net.postchain.common.BlockchainRid
import net.postchain.common.data.Hash
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.configurations.GTXTestModule
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.RestTools
import net.postchain.devtools.testinfra.TestOneOpGtxTransaction
import net.postchain.devtools.utils.configuration.SystemSetup
import net.postchain.devtools.utils.configuration.system.SystemSetupFactory
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvProofTreeTestHelper
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkle.proof.merkleHash
import net.postchain.gtx.GTXTransactionFactory
import net.postchain.integrationtest.JsonTools
import net.postchain.integrationtest.JsonTools.jsonAsMap
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfirmationProofSlowIntegrationTest : IntegrationTestSetup() {

    private val gson = JsonTools.buildGson()

    private val gtxTestModule = GTXTestModule()
    private val chainIid = 1
    private val nodeCount = 4
    private val bcConfFileName = "/net/postchain/devtools/api/blockchain_config.xml"

    private fun doSystemSetup(): SystemSetup {
        configOverrides.setProperty("testpeerinfos", createPeerInfos(nodeCount))
        val bcConfFileMap = mapOf(chainIid to bcConfFileName)
        val sysSetup = SystemSetupFactory.buildSystemSetup(bcConfFileMap)
        assertEquals(nodeCount, sysSetup.nodeMap.size, "We didn't get the nodes we expected, check BC config file")
        sysSetup.needRestApi = true // NOTE!! This is important in this test!!

        createNodesFromSystemSetup(sysSetup)
        return sysSetup
    }

    @Test
    fun testConfirmationProof() {
        val sysSetup = doSystemSetup()
        val blockchainRIDBytes = sysSetup.blockchainMap[chainIid]!!.rid
        val blockchainRID = blockchainRIDBytes.toHex()

        val factory = GTXTransactionFactory(blockchainRIDBytes, gtxTestModule, cryptoSystem)

        var blockHeight = 0
        var currentId = 0

        for (txCount in 1..16) {
            println("----------------- Running testConfirmationProof with txCount: $txCount ---------------------")
            val txList = mutableListOf<TestOneOpGtxTransaction>()
            for (i in 1..txCount) {
                txList.add(postGtxTransaction(factory, ++currentId, blockHeight, blockchainRIDBytes))
            }

            txList.forEach {
                // Wait for all txs to confirm. They are posted to different nodes and all
                // txs might not arrive at all nodes prior to block building. It's therefore not
                // enough to await last tx being confirmed on node 0.
                awaitConfirmed(blockchainRID, it.getRID())
            }

            txList.reverse() // We begin with the last TX that we saved from last step
            val txArr = txList.toTypedArray()

            blockHeight++

            for (i in 0 until txCount) {
                val realTx = txArr[i]
                val jsonResponse = fetchConfirmationProof(realTx, i, blockchainRIDBytes)
                checkConfirmationProofForTx(realTx, jsonResponse)
            }
        }
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
            bcRid: BlockchainRid
    ): TestOneOpGtxTransaction {
        val tx = TestOneOpGtxTransaction(factory, currentId)
        val strHexData = tx.getRawData().toHex()
        //println("Sending TX: $strHexData:")
        testStatusPost(
                blockHeight % nodeCount,
                "/tx/${bcRid.toHex()}",
                "{\"tx\": \"$strHexData\"}")

        return tx
    }

    /**
     * Fetch the confirmation proof from the server for the given TX.
     *
     * An example of what the JSON response might look like:
     *
     *  {
     *    "hash":"93A4..0F",
     *    "blockHeader":"A581..00",
     *    "signatures":[
     *      {
     *        "pubKey":"03A3..70",
     *        "signature":"3CE..F3"
     *      },
     *      {
     *        "pubKey":"031B..8F",
     *        "signature":"D5F3..E0"
     *      },
     *      {
     *        "pubKey":"03B2..94"
     *        ,"signature":"33C8..3E"
     *      }
     *    ],
     *    "merkleProofTree":[
     *      103,
     *      1,
     *      -10,
     *      [
     *        101,
     *        0,
     *        "93A4..0F"
     *      ],
     *      [
     *        100,
     *        "0000..00"
     *      ]
     *     ]
     *  }
     *
     *
     * @param realTx is the transaction we need to prove.
     * @param seqNr is just for debugging
     * @return the Json converted to a [Map]
     */
    private fun fetchConfirmationProof(realTx: TestOneOpGtxTransaction, seqNr: Int, bcRid: BlockchainRid): String {
        val txRidHex = realTx.getRID().toHex()
        println("Fetching conf proof for tx nr: $seqNr with tx RID: $txRidHex ")
        val body = given().port(nodes[0].getRestApiHttpPort())
                .get("/tx/${bcRid.toHex()}/${txRidHex}/confirmationProof")
                .then()
                .statusCode(200)
                .extract()
                .body().asString()

        println("Response: $body")

        return body
    }

    /**
     * Verify that the transaction is in the block, and verify that the confirmation proof is correct. It should have:
     *   2.a hash
     *   2.b signatures
     *   2.c merkle path
     *
     * @param realTx - the transaction to check
     * @param jsonBody - proof in JSON format
     */
    private fun checkConfirmationProofForTx(realTx: TestOneOpGtxTransaction, jsonBody: String) {
        val confirmationProofGtv = GtvDecoder.decodeGtv((jsonAsMap(gson, jsonBody)["proof"] as String).hexStringToByteArray())
        val confirmationProof = GtvObjectMapper.fromGtv(confirmationProofGtv, ConfirmationProof::class)

        // Assert tx hash
        assertArrayEquals(realTx.getHash(), confirmationProof.hash)

        // Assert signatures
        val blockHeaderRaw = confirmationProof.blockHeader
        val blockHeader = BaseBlockHeader(blockHeaderRaw, GtvMerkleHashCalculator(cryptoSystem))
        val blockRid = blockHeader.blockRID

        confirmationProof.witness.getSignatures().forEach {
            assertTrue(cryptoSystem.verifyDigest(blockRid, it))
        }

        val blockMerkleRootHashFromHeader = blockHeader.blockHeaderRec.getMerkleRootHash()
        println("blockMerkleRootHash - from header: ${blockMerkleRootHashFromHeader.toHex()}")
        // -------------------
        // Merkle Proof Tree
        // -------------------
        val merkleProofTree = confirmationProofGtv["merkleProofTree"]!! as GtvArray

        // a) Do we have the value to prove
        val found = GtvProofTreeTestHelper.findHashInBlockProof(realTx.getHash(), merkleProofTree)
        assertTrue(found, "The proof does not contain the hash we expected")

        // b) Calculate the merkle root of the proof
        val myNewBlockHash = confirmationProof.merkleProofTree.merkleHash(GtvMerkleHashCalculator(cryptoSystem))

        // Assert we get the same block RID
        println("Block merkle root - calculated : ${myNewBlockHash.toHex()}")
        assertTrue(myNewBlockHash.contentEquals(blockMerkleRootHashFromHeader),
                "The block merkle root calculated from the proof doesn't correspond to the block's merkle root hash from the header")
    }

    private fun awaitConfirmed(blockchainRID: String, txRid: Hash) {
        RestTools.awaitConfirmed(
                nodes[0].getRestApiHttpPort(),
                blockchainRID,
                txRid.toHex())
    }

    private fun testStatusPost(toIndex: Int, path: String, body: String) {
        given().port(nodes[toIndex].getRestApiHttpPort())
                .body(body)
                .post(path)
                .then()
                .statusCode(200)
    }
}
