// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.endpoint

import io.restassured.RestAssured
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.Query
import net.postchain.api.rest.controller.QueryResult
import net.postchain.api.rest.controller.RestApi
import net.postchain.core.ProgrammerMistake
import net.postchain.core.UserMistake
import org.hamcrest.core.IsEqual.equalTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RestApiQueryEndpointTest {

    private val basePath = "/api/v1"
    private val blockchainRID = "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3"
    private lateinit var restApi: RestApi
    private lateinit var model: Model

    @BeforeEach
    fun setup() {
        model = mock {
            on { chainIID } doReturn 1L
        }

        restApi = RestApi(0, basePath)
    }

    @AfterEach
    fun tearDown() {
        restApi.stop()
    }

    @Test
    fun test_query() {
        val queryString = """{"a"="b", "c"=3}"""
        val query = Query(queryString)

        val answerString = """{"d"=false}"""
        val answer = QueryResult(answerString)

        whenever(model.query(query)).thenReturn(answer)

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
            .body(queryString)
            .post("/query/$blockchainRID")
            .then()
            .statusCode(200)
            .body(equalTo(answerString))
    }

    /**
     * The idea here is to test that RestApi can handle when the model throws an exception during "query()" execution.
     */
    @Test
    fun test_query_error() {
        val queryString = """{"a"="b", "c"=3}"""
        val query = Query(queryString)

        val answerString = """{"error":"Bad bad stuff."}"""

        // Throw here
        whenever(model.query(query)).thenThrow(IllegalStateException("Bad bad stuff."))

        restApi.attachModel(blockchainRID, model)

        try {
            RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .body(queryString)
                .post("/query/$blockchainRID")
                .then()
                .statusCode(500)
                .body(equalTo(answerString))
        } catch (e: Exception) {
            fail("Should not bang during query, since the exception is converted to 500 message: $e")
        }
    }

    @Test
    fun test_query_UserError() {
        val queryString = """{"a"="b", "c"=3}"""
        val query = Query(queryString)

        val answerMessage = "expected error"
        val answerBody = """{"error":"expected error"}"""

        whenever(model.query(query)).thenThrow(
                UserMistake(answerMessage))

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .body(queryString)
                .post("/query/$blockchainRID")
                .then()
                .statusCode(400)
                .body(equalTo(answerBody))
    }

    @Test
    fun test_query_other_error() {
        val queryString = """{"a"="b", "c"=3}"""
        val query = Query(queryString)

        val answerMessage = "expected error"
        val answerBody = """{"error":"expected error"}"""

        whenever(model.query(query)).thenThrow(ProgrammerMistake(answerMessage))

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .body(queryString)
                .post("/query/$blockchainRID")
                .then()
                .statusCode(500)
                .body(equalTo(answerBody))
    }

    @Test
    fun test_query_when_blockchainRID_too_long_then_400_received() {
        val queryString = """{"a"="b", "c"=3}"""
        val answerBody = """{"error":"Invalid blockchainRID. Expected 64 hex digits [0-9a-fA-F]"}"""

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .body(queryString)
                .post("/query/${blockchainRID}0000")
                .then()
                .statusCode(400)
                .body(equalTo(answerBody))
    }

    @Test
    fun test_query_when_blockchainRID_too_short_then_400_received() {
        val queryString = """{"a"="b", "c"=3}"""
        val answerBody = """{"error":"Invalid blockchainRID. Expected 64 hex digits [0-9a-fA-F]"}"""

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .body(queryString)
                .post("/query/${blockchainRID.substring(1)}")
                .then()
                .statusCode(400)
                .body(equalTo(answerBody))
    }

    @Test
    fun test_query_when_blockchainRID_not_hex_then_400_received() {
        val queryString = """{"a"="b", "c"=3}"""
        val answerBody = """{"error":"Invalid blockchainRID. Expected 64 hex digits [0-9a-fA-F]"}"""

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .body(queryString)
                .post("/query/${blockchainRID.replaceFirst("a", "g")}")
                .then()
                .statusCode(400)
                .body(equalTo(answerBody))
    }
}