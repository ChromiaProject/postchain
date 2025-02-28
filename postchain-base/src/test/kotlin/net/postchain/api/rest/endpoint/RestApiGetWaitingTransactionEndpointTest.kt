// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.endpoint

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.isContentEqualTo
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.api.rest.model.TxRid
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.GtvDecoder
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class RestApiGetWaitingTransactionEndpointTest {

    private val basePath = "/api/v1"
    private lateinit var restApi: RestApi
    private lateinit var model: Model
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")
    private val txHashHex = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

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
    fun found() {
        val tx = "1234".hexStringToByteArray()
        whenever(model.getWaitingTransaction(TxRid(txHashHex.hexStringToByteArray())))
                .thenReturn(tx to Instant.ofEpochMilli(4711))

        restApi.attachModel(blockchainRID, model)

        val body = given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", ContentType.BINARY)
                .get("/tx/$blockchainRID/waiting/$txHashHex")
                .then()
                .statusCode(200)
                .header("X-Transaction-Timestamp", equalTo("4711"))
                .contentType(ContentType.BINARY)
        assertThat(body.extract().response().body.asByteArray()).isContentEqualTo(tx)
    }

    @Test
    fun when_not_found_then_404_received() {
        whenever(model.getWaitingTransaction(TxRid(txHashHex.hexStringToByteArray())))
                .thenReturn(null)
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/tx/$blockchainRID/waiting/$txHashHex")
                .then()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .body("error", equalTo("Can't find waiting transaction with RID: AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))
    }

    @Test
    fun `Errors are in GTV format when querying for GTV`() {
        whenever(model.getWaitingTransaction(TxRid(txHashHex.hexStringToByteArray())))
                .thenReturn(null)
        restApi.attachModel(blockchainRID, model)

        val body = given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", ContentType.BINARY)
                .get("/tx/$blockchainRID/waiting/$txHashHex")
                .then()
                .statusCode(404)
                .contentType(ContentType.BINARY)
        assertThat(GtvDecoder.decodeGtv(body.extract().response().body.asByteArray()).asString())
                .isEqualTo("Can't find waiting transaction with RID: AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
    }

    @Test
    fun test_getTx_when_txHash_too_long_then_400_received() {
        whenever(model.getTransaction(TxRid(txHashHex.hexStringToByteArray())))
                .thenReturn(null)

        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/tx/$blockchainRID/waiting/${txHashHex}0000")
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("error", containsString("txRid"))
    }

    @Test
    fun test_getTx_when_txHash_too_short_then_400_received() {
        whenever(model.getTransaction(TxRid(txHashHex.hexStringToByteArray())))
                .thenReturn(null)

        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/tx/$blockchainRID/waiting/${txHashHex.substring(1)}")
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("error", containsString("txRid"))
    }

    @Test
    fun test_getTx_when_txHash_not_hex_then_400_received() {
        whenever(model.getTransaction(TxRid(txHashHex.hexStringToByteArray())))
                .thenReturn(null)

        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/tx/$blockchainRID/waiting/${txHashHex.replaceFirst("a", "g")}")
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("error", containsString("txRid"))
    }
}