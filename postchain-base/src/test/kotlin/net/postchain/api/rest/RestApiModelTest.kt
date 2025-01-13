// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest

import io.restassured.RestAssured.given
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.api.rest.model.TxRid
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RestApiModelTest {

    private val basePath = "/api/v1"
    private lateinit var restApi: RestApi
    private lateinit var model: Model
    private val blockchainRID1 = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a1")
    private val blockchainRID2 = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a2")
    private val blockchainRID3 = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")
    private val blockchainRIDBadFormatted = "78967baa4768cbcef11c50"
    private val txRID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

    @BeforeEach
    fun setup() {
        model = mock {
            on { chainIID } doReturn 1L
            on { blockchainRid } doReturn blockchainRID1
            on { live } doReturn true
        }

        restApi = RestApi(0, basePath, gracefulShutdown = false)

        // We're doing this test by test instead
        // restApi.attachModel(blockchainRID, model)
    }

    @AfterEach
    fun tearDown() {
        restApi.close()
    }

    @Test
    fun testGetTx_no_models_404_received() {
        given().basePath(basePath).port(restApi.actualPort())
                .get("/tx/$blockchainRID1/$txRID")
                .then()
                .statusCode(404)
    }

    @Test
    fun testGetTx_unknown_model_404_received() {
        restApi.attachModel(blockchainRID1, model)
        restApi.attachModel(blockchainRID2, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/tx/$blockchainRID3/$txRID")
                .then()
                .statusCode(404)
    }

    @Test
    fun testQuery_unavailable_model_503_received() {
        val unavailableModel: Model = mock {
            on { chainIID } doReturn 1L
            on { blockchainRid } doReturn blockchainRID1
            on { live } doReturn false
        }

        restApi.attachModel(blockchainRID1, unavailableModel)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/query/$blockchainRID1/?type=my-query")
                .then()
                .statusCode(503)
    }

    @Test
    fun testGetTx_case_insensitive_ok() {
        whenever(
                model.getTransaction(TxRid(txRID.hexStringToByteArray()))
        ).thenReturn("1234".hexStringToByteArray())

        restApi.attachModel(blockchainRID1, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/tx/${blockchainRID1}/$txRID")
                .then()
                .statusCode(200)
    }

    @Test
    fun testGetTx_attach_then_detach_ok() {
        whenever(
                model.getTransaction(TxRid(txRID.hexStringToByteArray()))
        ).thenReturn("1234".hexStringToByteArray())

        restApi.attachModel(blockchainRID1, model)
        given().basePath(basePath).port(restApi.actualPort())
                .get("/tx/$blockchainRID1/$txRID")
                .then()
                .statusCode(200)

        restApi.detachModel(blockchainRID1)
        given().basePath(basePath).port(restApi.actualPort())
                .get("/tx/$blockchainRID1/$txRID")
                .then()
                .statusCode(404)

    }

    @Test
    fun testGetTx_incorrect_blockchainRID_format() {
        restApi.attachModel(blockchainRID1, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/tx/$blockchainRIDBadFormatted/$txRID")
                .then()
                .statusCode(400)
    }
}
