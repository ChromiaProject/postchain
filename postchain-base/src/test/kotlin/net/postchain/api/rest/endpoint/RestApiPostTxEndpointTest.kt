// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.endpoint

import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.common.toHex
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import org.hamcrest.core.IsEqual.equalTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import kotlin.test.assertContentEquals

class RestApiPostTxEndpointTest {

    private val basePath = "/api/v1"
    private val blockchainRID = "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3"
    private lateinit var restApi: RestApi
    private lateinit var model: Model

    @BeforeEach
    fun setup() {
        model = mock {
            on { chainIID } doReturn 1L
            on { live } doReturn true
        }

        restApi = RestApi(0, basePath)
    }

    @AfterEach
    fun tearDown() {
        restApi.stop()
    }

    @Test
    fun test_postTx_binary_Ok() {
        val tx = "hello".toByteArray()

        restApi.attachModel(blockchainRID, model)

        val body = given().basePath(basePath).port(restApi.actualPort())
                .contentType(ContentType.BINARY)
                .accept(ContentType.BINARY)
                .body(tx)
                .post("/tx/$blockchainRID")
                .then()
                .statusCode(200)
                .contentType(ContentType.BINARY)
        assertContentEquals(GtvEncoder.encodeGtv(gtv(mapOf())), body.extract().body().asByteArray())

        verify(model, times(1)).postTransaction(tx)
    }

    @Test
    fun test_postTx_Json_Ok() {
        val tx = "hello".toByteArray()

        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .body("{\"tx\": \"${tx.toHex()}\"}")
                .post("/tx/$blockchainRID")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(equalTo("{}"))

        verify(model, times(1)).postTransaction(tx)
    }

    @Test
    fun test_postTx_when_empty_message_then_400_received() {
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .body("")
                .post("/tx/$blockchainRID")
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("error", equalTo("Invalid tx format. Expected {\"tx\": <hex-string>}"))
    }

    @Test
    fun test_postTx_when_empty_message_then_400_received_binary() {
        restApi.attachModel(blockchainRID, model)

        val body = given().basePath(basePath).port(restApi.actualPort())
                .accept(ContentType.BINARY)
                .body("")
                .post("/tx/$blockchainRID")
                .then()
                .statusCode(400)
                .contentType(ContentType.BINARY)
        assertContentEquals(
                GtvEncoder.encodeGtv(gtv("Invalid tx format. Expected {\"tx\": <hex-string>}")),
                body.extract().body().asByteArray())
    }

    @Test
    fun test_postTx_when_missing_tx_property_then_400_received() {
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .body("{}")
                .post("/tx/$blockchainRID")
                .then()
                .statusCode(400)
    }

    @Test
    fun test_postTx_when_empty_tx_property_then_400_received() {
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .body("{\"tx\": \"\"}")
                .post("/tx/$blockchainRID")
                .then()
                .statusCode(400)
    }

    @Test
    fun test_postTx_when_tx_property_not_hex_then_400_received() {
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .body("{\"tx\": \"abc123z\"}")
                .post("/tx/$blockchainRID")
                .then()
                .statusCode(400)
    }

    @Test
    fun test_postTx_when_invalid_json_then_400_received() {
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .body("a")
                .post("/tx/$blockchainRID")
                .then()
                .statusCode(400)
    }

    @Test
    fun test_postTx_when_blockchainRID_too_long_then_400_received() {
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .body("a")
                .post("/tx/${blockchainRID}0000")
                .then()
                .statusCode(400)
    }

    @Test
    fun test_postTx_when_blockchainRID_too_short_then_400_received() {
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .body("a")
                .post("/tx/${blockchainRID.substring(1)}")
                .then()
                .statusCode(400)
    }

    @Test
    fun test_postTx_when_blockchainRID_not_hex_then_400_received() {
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .body("a")
                .post("/tx/${blockchainRID.replaceFirst("a", "g")}")
                .then()
                .statusCode(400)
    }
}