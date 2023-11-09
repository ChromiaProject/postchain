// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.endpoint

import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.api.rest.json.JsonFactory
import net.postchain.api.rest.model.TxRid
import net.postchain.base.cryptoSystem
import net.postchain.common.BlockchainRid
import net.postchain.common.toHex
import net.postchain.core.BlockRid
import net.postchain.core.TransactionInfoExt
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RestApiGetTxInfoEndpointTest {

    private val basePath = "/api/v1"
    private lateinit var restApi: RestApi
    private lateinit var model: Model
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")
    private val gson = JsonFactory.makeJson()

    @BeforeEach
    fun setup() {
        model = mock {
            on { chainIID } doReturn 1L
            on { blockchainRid } doReturn blockchainRID
            on { live } doReturn true
        }

        restApi = RestApi(0, basePath, gracefulShutdown = false, clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
    }

    @AfterEach
    fun tearDown() {
        restApi.close()
    }

    @Test
    fun testGetTxInfo() {
        val tx = "tx2".toByteArray()
        val txRID = cryptoSystem.digest(tx)
        val response = TransactionInfoExt(BlockRid.buildRepeat(4).data, 3, "guess what? Another header".toByteArray(),
                "signatures".toByteArray(), 1574849940, txRID, "tx2 - 002".toByteArray().slice(IntRange(0, 4)).toByteArray(), tx)

        whenever(
                model.getTransactionInfo(TxRid(txRID))
        ).thenReturn(response)
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/transactions/$blockchainRID/${txRID.toHex()}")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .header("Cache-Control", equalTo("public, max-age=31536000"))
                .header("Expires", equalTo("Fri, 1 Jan 1971 00:00:00 GMT"))
                .body("blockRID", equalTo("0404040404040404040404040404040404040404040404040404040404040404"))
    }

    @Test
    fun testGetTxInfoNotFound() {
        val tx = "tx2".toByteArray()
        val txRID = cryptoSystem.digest(tx)

        whenever(
                model.getTransactionInfo(TxRid(txRID))
        ).thenReturn(null)
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/transactions/$blockchainRID/${txRID.toHex()}")
                .then()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .header("Cache-Control", nullValue())
                .header("Expires", nullValue())
                .body("error", equalTo("Can't find tx with hash ${txRID.toHex()}"))
    }

    @Test
    fun testGetTransactionsWithLimit() {
        val response = listOf(
                TransactionInfoExt(BlockRid.buildRepeat(2).data, 1, "some other header".toByteArray(), "signatures".toByteArray(), 1574849760, cryptoSystem.digest("tx1".toByteArray()), "tx1 - 001".toByteArray().slice(IntRange(0, 4)).toByteArray(), "tx1".toByteArray()),
                TransactionInfoExt(BlockRid.buildRepeat(4).data, 3, "guess what? Another header".toByteArray(), "signatures".toByteArray(), 1574849940, cryptoSystem.digest("tx2".toByteArray()), "tx2 - 002".toByteArray().slice(IntRange(0, 4)).toByteArray(), "tx2".toByteArray()),
                TransactionInfoExt(BlockRid.buildRepeat(4).data, 3, "guess what? Another header".toByteArray(), "signatures".toByteArray(), 1574849940, cryptoSystem.digest("tx3".toByteArray()), "tx3 - 003".toByteArray().slice(IntRange(0, 4)).toByteArray(), "tx3".toByteArray()),
                TransactionInfoExt(BlockRid.buildRepeat(4).data, 3, "guess what? Another header".toByteArray(), "signatures".toByteArray(), 1574849940, cryptoSystem.digest("tx4".toByteArray()), "tx4 - 004".toByteArray().slice(IntRange(0, 4)).toByteArray(), "tx4".toByteArray())
        )
        whenever(
                model.getTransactionsInfo(Long.MAX_VALUE, 300)
        ).thenReturn(response)
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/transactions/$blockchainRID?before-time=${Long.MAX_VALUE}&limit=${300}")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .header("Cache-Control", equalTo("private, must-revalidate"))
                .header("Expires", equalTo("0"))
                .body(equalTo(gson.toJson(response).toString()))
    }

    @Test
    fun testGetTransactionsWithoutParams() {
        val response = listOf(
                TransactionInfoExt(BlockRid.buildRepeat(2).data, 1, "some other header".toByteArray(), "signatures".toByteArray(), 1574849760, cryptoSystem.digest("tx1".toByteArray()), "tx1 - 001".toByteArray().slice(IntRange(0, 4)).toByteArray(), "tx1".toByteArray()),
                TransactionInfoExt(BlockRid.buildRepeat(4).data, 3, "guess what? Another header".toByteArray(), "signatures".toByteArray(), 1574849940, cryptoSystem.digest("tx2".toByteArray()), "tx2 - 002".toByteArray().slice(IntRange(0, 4)).toByteArray(), "tx2".toByteArray()),
                TransactionInfoExt(BlockRid.buildRepeat(4).data, 3, "guess what? Another header".toByteArray(), "signatures".toByteArray(), 1574849940, cryptoSystem.digest("tx3".toByteArray()), "tx3 - 003".toByteArray().slice(IntRange(0, 4)).toByteArray(), "tx3".toByteArray()),
                TransactionInfoExt(BlockRid.buildRepeat(4).data, 3, "guess what? Another header".toByteArray(), "signatures".toByteArray(), 1574849940, cryptoSystem.digest("tx4".toByteArray()), "tx4 - 004".toByteArray().slice(IntRange(0, 4)).toByteArray(), "tx4".toByteArray())
        )
        whenever(
                model.getTransactionsInfo(Long.MAX_VALUE, 25)
        ).thenReturn(response)
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
                model.getTransactionsInfo(Long.MAX_VALUE, 25)
        ).thenReturn(response)
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
