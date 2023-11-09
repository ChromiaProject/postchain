// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.endpoint

import com.google.gson.JsonObject
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import net.postchain.api.rest.controller.DebugApi
import net.postchain.api.rest.controller.DebugInfoQuery
import net.postchain.api.rest.json.JsonFactory
import net.postchain.common.BlockchainRid
import net.postchain.debug.ErrorDiagnosticValue
import net.postchain.debug.JsonNodeDiagnosticContext
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RestApiDebugEndpointTest {

    private lateinit var debugInfoQuery: DebugInfoQuery
    private lateinit var debugApi: DebugApi
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")
    private val prettyGson = JsonFactory.makePrettyJson()

    @BeforeEach
    fun setup() {
        debugInfoQuery = mock()

        debugApi = DebugApi(0, debugInfoQuery, gracefulShutdown = false)
    }

    @AfterEach
    fun tearDown() {
        debugApi.close()
    }

    @Test
    fun `debug response is pretty`() {
        val response = JsonObject().apply {
            addProperty("foo", "bar")
        }

        whenever(
                debugInfoQuery.queryDebugInfo(null)
        ).thenReturn(response)

        given().basePath("/").port(debugApi.actualPort())
                .get("/_debug")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(equalTo(prettyGson.toJson(response)))
    }

    @Test
    fun someErrorsInDebug() {
        val nodeDiagnosticContext = JsonNodeDiagnosticContext()
        nodeDiagnosticContext.blockchainErrorQueue(blockchainRID).add(ErrorDiagnosticValue("foo", 42L))
        nodeDiagnosticContext.blockchainErrorQueue(blockchainRID).add(ErrorDiagnosticValue("bar", 42L, 10))
        val response = nodeDiagnosticContext.format()
        whenever(
                debugInfoQuery.queryDebugInfo(null)
        ).thenReturn(response)

        given().basePath("/").port(debugApi.actualPort())
                .get("/_debug")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(equalTo(prettyGson.toJson(response)))
    }
}
