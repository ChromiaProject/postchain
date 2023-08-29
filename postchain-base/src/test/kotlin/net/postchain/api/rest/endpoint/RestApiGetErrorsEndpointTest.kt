// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.endpoint

import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.common.BlockchainRid
import net.postchain.debug.EagerDiagnosticValue
import net.postchain.debug.JsonNodeDiagnosticContext
import net.postchain.debug.NodeDiagnosticContext
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.collection.IsCollectionWithSize.hasSize
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RestApiGetErrorsEndpointTest {

    private val basePath = "/api/v1"
    private lateinit var restApi: RestApi
    private lateinit var model: Model
    private lateinit var nodeDiagnosticContext: NodeDiagnosticContext
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")


    @BeforeEach
    fun setup() {
        model = mock {
            on { chainIID } doReturn 1L
            on { blockchainRid } doReturn blockchainRID
            on { live } doReturn true
        }

        nodeDiagnosticContext = JsonNodeDiagnosticContext()

        restApi = RestApi(0, basePath, gracefulShutdown = false, nodeDiagnosticContext = nodeDiagnosticContext)
    }

    @AfterEach
    fun tearDown() {
        restApi.close()
    }

    @Test
    fun noErrors() {
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/errors/$blockchainRID")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("", hasSize<String>(0))
    }

    @Test
    fun someErrors() {
        restApi.attachModel(blockchainRID, model)
        nodeDiagnosticContext.blockchainErrorQueue(blockchainRID).add(EagerDiagnosticValue("foo"))
        nodeDiagnosticContext.blockchainErrorQueue(blockchainRID).add(EagerDiagnosticValue("bar"))

        given().basePath(basePath).port(restApi.actualPort())
                .get("/errors/$blockchainRID")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("", hasSize<String>(2))
                .body("[0]", equalTo("foo"))
                .body("[1]", equalTo("bar"))
    }

    @Test
    fun errorInUnavaliableChain() {
        whenever(model.live).thenReturn(false)

        restApi.attachModel(blockchainRID, model)
        nodeDiagnosticContext.blockchainErrorQueue(blockchainRID).add(EagerDiagnosticValue("foobar"))

        given().basePath(basePath).port(restApi.actualPort())
                .get("/errors/$blockchainRID")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("", hasSize<String>(1))
                .body("[0]", equalTo("foobar"))
    }

    @Test
    fun chainNotFound() {
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/errors/78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a4")
                .then()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .body("error", equalTo("Can't find blockchain with blockchainRID: 78967BAA4768CBCEF11C508326FFB13A956689FCB6DC3BA17F4B895CBB1577A4"))
    }
}
