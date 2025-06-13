// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.endpoint

import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import net.postchain.api.rest.controller.DATA_TRUNCATED_HEADER
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.api.rest.infra.RestApiConfig.Companion.DEFAULT_MAX_DATA_SIZE
import net.postchain.api.rest.json.JsonFactory
import net.postchain.api.rest.model.TxRid
import net.postchain.base.BaseBlockWitness
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.core.BlockRid
import net.postchain.core.TransactionInfoExt
import net.postchain.core.TransactionInfoExtsTruncated
import net.postchain.core.block.BlockQueryTimeFilter
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.Signature
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.crypto.sha256Digest
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.gtx.Gtx
import net.postchain.gtx.GtxBody
import net.postchain.gtx.GtxOp
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.Matchers.hasKey
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RestApiGetTxInfoEndpointTest {

    private val basePath = "/api/v1"
    private lateinit var restApi: RestApi
    private lateinit var model: Model
    private val gson = JsonFactory.makeJson()
    private val hashCalculator = GtvMerkleHashCalculatorV2(::sha256Digest)
    private val cryptoSystem = Secp256K1CryptoSystem()
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")
    private val witness1 = "0320F0B9E7ECF1A1568C31644B04D37ADC05327F996B9F48220E301DC2FEE6F8FF".hexStringToByteArray()
    private val witness2 = "0307C88BF37C528B14AF95E421749E72F6DA88790BCE74890BDF780D854D063C40".hexStringToByteArray()
    private val signature1 = ByteArray(16) { 1 }
    private val signature2 = ByteArray(16) { 2 }
    private val witness = BaseBlockWitness.fromSignatures(arrayOf(
            Signature(witness1, signature1),
            Signature(witness2, signature2)
    ))

    @BeforeEach
    fun setup() {
        model = mock {
            on { chainIID } doReturn 1L
            on { blockchainRid } doReturn blockchainRID
            on { live } doReturn true
        }

        restApi = RestApi(0, basePath, gracefulShutdown = false)
    }

    @AfterEach
    fun tearDown() {
        restApi.close()
    }

    @Test
    fun `get tx info with data default`() {
        val tx = "tx2".toByteArray()
        val txRID = cryptoSystem.digest(tx)
        val response = TransactionInfoExt(BlockRid.buildRepeat(4).data, 3, "guess what? Another header".toByteArray(),
                witness = witness.getRawData(), 1574849940, txRID, "tx2 - 002".toByteArray().slice(IntRange(0, 4)).toByteArray(), tx)

        whenever(
                model.getTransactionInfo(TxRid(txRID), true)
        ).thenReturn(response)
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/transactions/$blockchainRID/${txRID.toHex()}")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .header("Cache-Control", equalTo("public, max-age=31536000"))
                .body("blockRID", equalTo("0404040404040404040404040404040404040404040404040404040404040404"))
                .body("witness", equalTo(witness.getRawData().toHex()))
                .body("witnesses", hasItems(witness1.toHex(), witness2.toHex()))
                .body("witnessSignatures", hasItems(signature1.toHex(), signature2.toHex()))
                .body("txData", equalTo(tx.toHex()))
    }

    @Test
    fun `get tx info with data`() {
        val tx = "tx2".toByteArray()
        val txRID = cryptoSystem.digest(tx)
        val response = TransactionInfoExt(BlockRid.buildRepeat(4).data, 3, "guess what? Another header".toByteArray(),
                witness = witness.getRawData(), 1574849940, txRID, "tx2 - 002".toByteArray().slice(IntRange(0, 4)).toByteArray(), tx)

        whenever(
                model.getTransactionInfo(TxRid(txRID), true)
        ).thenReturn(response)
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/transactions/$blockchainRID/${txRID.toHex()}?tx-data=true")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .header("Cache-Control", equalTo("public, max-age=31536000"))
                .body("blockRID", equalTo("0404040404040404040404040404040404040404040404040404040404040404"))
                .body("txData", equalTo(tx.toHex()))
                .body("$", not(hasKey("tx"))) // Ensures it’s absent.
    }

    @Test
    fun `get tx info without data`() {
        val tx = "tx2".toByteArray()
        val txRID = cryptoSystem.digest(tx)
        val response = TransactionInfoExt(BlockRid.buildRepeat(4).data, 3, "guess what? Another header".toByteArray(),
                witness = witness.getRawData(), 1574849940, txRID, "tx2 - 002".toByteArray().slice(IntRange(0, 4)).toByteArray(), null)

        whenever(
                model.getTransactionInfo(TxRid(txRID), false)
        ).thenReturn(response)
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/transactions/$blockchainRID/${txRID.toHex()}?tx-data=false")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .header("Cache-Control", equalTo("public, max-age=31536000"))
                .body("blockRID", equalTo("0404040404040404040404040404040404040404040404040404040404040404"))
                .body("$", not(hasKey("txData"))) // Ensures it’s absent.
                .body("$", not(hasKey("tx"))) // Ensures it’s absent.
    }

    @Test
    fun `get tx info with decoded data`() {
        val gtxBody = GtxBody(blockchainRID, listOf(
                GtxOp("foo", gtv(17), gtv("the arg")),
                GtxOp("bar", gtv(listOf(gtv(4711), gtv("some data")))),
        ), listOf(KeyPairHelper.pubKey(1), KeyPairHelper.pubKey(2)))
        val txRID = gtxBody.calculateTxRid(hashCalculator)
        val sig1 = cryptoSystem.buildSigMaker(KeyPairHelper.keyPair(1)).signDigest(txRID)
        val sig2 = cryptoSystem.buildSigMaker(KeyPairHelper.keyPair(2)).signDigest(txRID)
        val tx = Gtx(gtxBody, listOf(sig1.data, sig2.data)).encode()
        val response = TransactionInfoExt(BlockRid.buildRepeat(4).data, 3, "guess what? Another header".toByteArray(),
                witness = witness.getRawData(), 1574849940, txRID, "tx2 - 002".toByteArray().slice(IntRange(0, 4)).toByteArray(), tx)

        whenever(
                model.getTransactionInfo(TxRid(txRID), true)
        ).thenReturn(response)
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/transactions/$blockchainRID/${txRID.toHex()}?decode-tx=true")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .header("Cache-Control", equalTo("public, max-age=31536000"))
                .body("blockRID", equalTo("0404040404040404040404040404040404040404040404040404040404040404"))
                .body("$", not(hasKey("txData"))) // Ensures it’s absent.
                .body("tx.operations[0].name", equalTo("foo"))
                .body("tx.operations[0].args[0]", equalTo(17))
                .body("tx.operations[0].args[1]", equalTo("the arg"))
                .body("tx.operations[1].name", equalTo("bar"))
                .body("tx.operations[1].args[0][0]", equalTo(4711))
                .body("tx.operations[1].args[0][1]", equalTo("some data"))
                .body("tx.signers[0]", equalTo(KeyPairHelper.pubKey(1).toHex()))
                .body("tx.signers[1]", equalTo(KeyPairHelper.pubKey(2).toHex()))
    }

    @Test
    fun `get tx info not found`() {
        val tx = "tx2".toByteArray()
        val txRID = cryptoSystem.digest(tx)

        whenever(
                model.getTransactionInfo(TxRid(txRID), true)
        ).thenReturn(null)
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/transactions/$blockchainRID/${txRID.toHex()}")
                .then()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .header("Cache-Control", nullValue())
                .body("error", equalTo("Can't find transaction with RID: ${txRID.toHex()}"))
    }

    @Test
    fun testGetTransactionsWithLimit() {
        val response = listOf(
                TransactionInfoExt(BlockRid.buildRepeat(2).data, 1, "some other header".toByteArray(), witness = witness.getRawData(), 1574849760, cryptoSystem.digest("tx1".toByteArray()), "tx1 - 001".toByteArray().slice(IntRange(0, 4)).toByteArray(), "tx1".toByteArray()),
                TransactionInfoExt(BlockRid.buildRepeat(4).data, 3, "guess what? Another header".toByteArray(), witness = witness.getRawData(), 1574849940, cryptoSystem.digest("tx2".toByteArray()), "tx2 - 002".toByteArray().slice(IntRange(0, 4)).toByteArray(), "tx2".toByteArray()),
                TransactionInfoExt(BlockRid.buildRepeat(4).data, 3, "guess what? Another header".toByteArray(), witness = witness.getRawData(), 1574849940, cryptoSystem.digest("tx3".toByteArray()), "tx3 - 003".toByteArray().slice(IntRange(0, 4)).toByteArray(), "tx3".toByteArray()),
                TransactionInfoExt(BlockRid.buildRepeat(4).data, 3, "guess what? Another header".toByteArray(), witness = witness.getRawData(), 1574849940, cryptoSystem.digest("tx4".toByteArray()), "tx4 - 004".toByteArray().slice(IntRange(0, 4)).toByteArray(), "tx4".toByteArray())
        )
        whenever(
                model.getTransactionsInfo(BlockQueryTimeFilter(), 300, DEFAULT_MAX_DATA_SIZE)
        ).thenReturn(TransactionInfoExtsTruncated(response, false))
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/transactions/$blockchainRID?before-time=${Long.MAX_VALUE}&limit=${300}")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .header("Cache-Control", equalTo("private, must-revalidate"))
                .header(DATA_TRUNCATED_HEADER, equalTo("false"))
                .body(equalTo(gson.toJson(response).toString()))
    }

    @Test
    fun testGetTransactionsWithRemainingBlocks() {
        val response = listOf(
                TransactionInfoExt(BlockRid.buildRepeat(2).data, 1, "some other header".toByteArray(), witness = witness.getRawData(), 1574849760, cryptoSystem.digest("tx1".toByteArray()), "tx1 - 001".toByteArray().slice(IntRange(0, 4)).toByteArray(), "tx1".toByteArray()),
                TransactionInfoExt(BlockRid.buildRepeat(4).data, 3, "guess what? Another header".toByteArray(), witness = witness.getRawData(), 1574849940, cryptoSystem.digest("tx2".toByteArray()), "tx2 - 002".toByteArray().slice(IntRange(0, 4)).toByteArray(), "tx2".toByteArray()),
                TransactionInfoExt(BlockRid.buildRepeat(4).data, 3, "guess what? Another header".toByteArray(), witness = witness.getRawData(), 1574849940, cryptoSystem.digest("tx3".toByteArray()), "tx3 - 003".toByteArray().slice(IntRange(0, 4)).toByteArray(), "tx3".toByteArray()),
                TransactionInfoExt(BlockRid.buildRepeat(4).data, 3, "guess what? Another header".toByteArray(), witness = witness.getRawData(), 1574849940, cryptoSystem.digest("tx4".toByteArray()), "tx4 - 004".toByteArray().slice(IntRange(0, 4)).toByteArray(), "tx4".toByteArray())
        )
        whenever(
                model.getTransactionsInfo(BlockQueryTimeFilter(), 300, DEFAULT_MAX_DATA_SIZE)
        ).thenReturn(TransactionInfoExtsTruncated(response, true))

        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/transactions/$blockchainRID?before-time=${Long.MAX_VALUE}&limit=${300}")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .header("Cache-Control", equalTo("private, must-revalidate"))
                .header(DATA_TRUNCATED_HEADER, equalTo("true"))
                .body(equalTo(gson.toJson(response).toString()))
    }

    @Test
    fun testGetTransactionsWithoutParams() {
        val response = listOf(
                TransactionInfoExt(BlockRid.buildRepeat(2).data, 1, "some other header".toByteArray(), witness = witness.getRawData(), 1574849760, cryptoSystem.digest("tx1".toByteArray()), "tx1 - 001".toByteArray().slice(IntRange(0, 4)).toByteArray(), "tx1".toByteArray()),
                TransactionInfoExt(BlockRid.buildRepeat(4).data, 3, "guess what? Another header".toByteArray(), witness = witness.getRawData(), 1574849940, cryptoSystem.digest("tx2".toByteArray()), "tx2 - 002".toByteArray().slice(IntRange(0, 4)).toByteArray(), "tx2".toByteArray()),
                TransactionInfoExt(BlockRid.buildRepeat(4).data, 3, "guess what? Another header".toByteArray(), witness = witness.getRawData(), 1574849940, cryptoSystem.digest("tx3".toByteArray()), "tx3 - 003".toByteArray().slice(IntRange(0, 4)).toByteArray(), "tx3".toByteArray()),
                TransactionInfoExt(BlockRid.buildRepeat(4).data, 3, "guess what? Another header".toByteArray(), witness = witness.getRawData(), 1574849940, cryptoSystem.digest("tx4".toByteArray()), "tx4 - 004".toByteArray().slice(IntRange(0, 4)).toByteArray(), "tx4".toByteArray())
        )
        whenever(
                model.getTransactionsInfo(BlockQueryTimeFilter(), 25, DEFAULT_MAX_DATA_SIZE)
        ).thenReturn(TransactionInfoExtsTruncated(response, false))
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/transactions/$blockchainRID")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(equalTo(gson.toJson(response).toString()))
    }

    @Test
    fun testGetTransactionsEmpty() {
        val response = listOf<TransactionInfoExt>()
        whenever(
                model.getTransactionsInfo(BlockQueryTimeFilter(), 25, DEFAULT_MAX_DATA_SIZE)
        ).thenReturn(TransactionInfoExtsTruncated(response, false))
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/transactions/$blockchainRID")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(equalTo("[]"))
    }

    @Test
    fun testGetTransactionsWithInvalidSigner() {
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/transactions/$blockchainRID?signer=qrst")
                .then()
                .statusCode(400)
    }
}
