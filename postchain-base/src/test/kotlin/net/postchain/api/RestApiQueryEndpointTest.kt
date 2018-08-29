package net.postchain.api

import io.restassured.RestAssured
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.Query
import net.postchain.api.rest.controller.QueryResult
import net.postchain.api.rest.controller.RestApi
import net.postchain.core.ProgrammerMistake
import net.postchain.core.UserMistake
import org.easymock.EasyMock.*
import org.hamcrest.core.IsEqual.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test

class RestApiQueryEndpointTest {

    private val basePath = "/api/v1"
    private lateinit var restApi: RestApi
    private lateinit var model: Model

    @Before
    fun setup() {
        model = createMock(Model::class.java)
        restApi = RestApi(model, 0, basePath)
    }

    @After
    fun tearDown() {
        restApi.stop()
    }

    @Test
    fun test_query() {
        val queryString = """{"a"="b", "c"=3}"""
        val query = Query(queryString)

        val answerString = """{"d"=false}"""
        val answer = QueryResult(answerString)

        expect(model.query(query)).andReturn(answer)
        replay(model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .body(queryString)
                .post("/query")
                .then()
                .statusCode(200)
                .body(equalTo(answerString))

        verify(model)
    }

    @Test
    fun test_query_UserError() {
        val queryString = """{"a"="b", "c"=3}"""
        val query = Query(queryString)

        val answerMessage = "expected error"
        val answerBody = """{"error":"expected error"}"""

        expect(model.query(query)).andThrow(
                UserMistake(answerMessage))
        replay(model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .body(queryString)
                .post("/query")
                .then()
                .statusCode(400)
                .body(equalTo(answerBody))

        verify(model)
    }

    @Test
    fun test_query_other_error() {
        val queryString = """{"a"="b", "c"=3}"""
        val query = Query(queryString)

        val answerMessage = "expected error"
        val answerBody = """{"error":"expected error"}"""

        expect(model.query(query)).andThrow(
                ProgrammerMistake(answerMessage))
        replay(model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .body(queryString)
                .post("/query")
                .then()
                .statusCode(500)
                .body(equalTo(answerBody))

        verify(model)
    }
}