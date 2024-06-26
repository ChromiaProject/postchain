// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.endpoint

import assertk.assertThat
import assertk.isContentEqualTo
import io.restassured.RestAssured
import io.restassured.http.ContentType
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.gtvToJSON
import net.postchain.gtv.make_gtv_gson
import net.postchain.gtx.GtxQuery
import net.postchain.gtx.NON_STRICT_QUERY_ARGUMENT
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.core.IsEqual.equalTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * ProgrammerMistake -> 500
 * "Any standard exception" -> 500
 * UserMistake -> 400
 */
class RestApiQueryEndpointTest {

    private val basePath = "/api/v1"
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")
    private val gson = make_gtv_gson()
    private lateinit var restApi: RestApi
    private lateinit var model: Model

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
    fun test_post_query() {
        val queryMap = mapOf(
                "type" to gtv("test_query"),
                "a" to gtv("b"),
                "c" to gtv(3)
        )

        val queryString = gtvToJSON(gtv(queryMap), gson)
        val query = GtxQuery("test_query", gtv(mapOf("a" to gtv("b"), "c" to gtv(3), NON_STRICT_QUERY_ARGUMENT to gtv(true))))

        val answerString = """{"d":0}"""
        val answer = gtv(mapOf("d" to gtv(false)))

        whenever(model.query(query)).thenReturn(answer)

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .body(queryString)
                .post("/query/$blockchainRID")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(equalTo(answerString))
    }

    @Test
    fun test_get_query() {
        val queryMap = mapOf(
                "type" to gtv("test_query"),
                "a" to gtv("b"),
                "c" to gtv(3)
        )

        val queryString = queryMap.map { "${it.key}=${it.value.toString().trim('"')}" }.joinToString("&")
        val query = GtxQuery("test_query", gtv(mapOf("a" to gtv("b"), "c" to gtv(3), NON_STRICT_QUERY_ARGUMENT to gtv(true))))

        val answerString = """{"d":0}"""
        val answer = gtv(mapOf("d" to gtv(false)))

        whenever(model.query(query)).thenReturn(answer)
        whenever(model.queryCacheTtlSeconds).thenReturn(0L)

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .get("/query/$blockchainRID?$queryString")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .header("Cache-Control", nullValue())
                .header("Expires", nullValue())
                .body(equalTo(answerString))
    }

    @Test
    fun test_get_query_with_cache() {
        val queryMap = mapOf(
                "type" to gtv("test_query"),
                "a" to gtv("b"),
                "c" to gtv(3)
        )

        val queryString = queryMap.map { "${it.key}=${it.value.toString().trim('"')}" }.joinToString("&")
        val query = GtxQuery("test_query", gtv(mapOf("a" to gtv("b"), "c" to gtv(3), NON_STRICT_QUERY_ARGUMENT to gtv(true))))

        val answerString = """{"d":0}"""
        val answer = gtv(mapOf("d" to gtv(false)))

        whenever(model.query(query)).thenReturn(answer)
        whenever(model.queryCacheTtlSeconds).thenReturn(17L)

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .get("/query/$blockchainRID?$queryString")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .header("Cache-Control", equalTo("public, max-age=17"))
                .header("Expires", equalTo("Thu, 1 Jan 1970 00:00:17 GMT"))
                .body(equalTo(answerString))
    }

    @Test
    fun test_direct_query() {
        val queryMap = mapOf(
                "type" to gtv("test_query"),
                "a" to gtv("b"),
                "c" to gtv(3)
        )

        val queryString = queryMap.map { "${it.key}=${it.value.toString().trim('"')}" }.joinToString("&")
        val query = GtxQuery("test_query", gtv(mapOf("a" to gtv("b"), "c" to gtv(3), NON_STRICT_QUERY_ARGUMENT to gtv(true))))

        val answerString = "Hello, world!"
        val answer = gtv(gtv("text/plain"), gtv(answerString))

        whenever(model.query(query)).thenReturn(answer)
        whenever(model.queryCacheTtlSeconds).thenReturn(17L)

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .get("/dquery/$blockchainRID?$queryString")
                .then()
                .statusCode(200)
                .contentType(ContentType.TEXT)
                .header("Cache-Control", equalTo("public, max-age=17"))
                .header("Expires", equalTo("Thu, 1 Jan 1970 00:00:17 GMT"))
                .body(equalTo(answerString))
    }

    /**
     * The idea here is to test that RestApi can handle when the model throws an exception during "query()" execution.
     *
     * "Standard exceptions" -> 500
     */
    @Test
    fun test_query_error() {
        val queryMap = mapOf(
                "type" to gtv("test_query"),
                "a" to gtv("b"),
                "c" to gtv(3)
        )

        val queryString = gtvToJSON(gtv(queryMap), gson)
        val query = GtxQuery("test_query", gtv(mapOf("a" to gtv("b"), "c" to gtv(3), NON_STRICT_QUERY_ARGUMENT to gtv(true))))

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
                .contentType(ContentType.JSON)
                .body(equalTo(answerString))
        } catch (e: Exception) {
            fail("Should not bang during query, since the exception is converted to 500 message: $e")
        }
    }

    /**
     * ProgrammerMistake -> 500
     */
    @Test
    fun test_query_other_error() {
        val queryMap = mapOf(
                "type" to gtv("test_query"),
                "a" to gtv("b"),
                "c" to gtv(3)
        )

        val queryString = gtvToJSON(gtv(queryMap), gson)
        val query = GtxQuery("test_query", gtv(mapOf("a" to gtv("b"), "c" to gtv(3), NON_STRICT_QUERY_ARGUMENT to gtv(true))))

        val answerMessage = "expected error"
        val answerBody = """{"error":"expected error"}"""

        whenever(model.query(query)).thenThrow(ProgrammerMistake(answerMessage))

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .body(queryString)
                .post("/query/$blockchainRID")
                .then()
                .statusCode(500)
                .contentType(ContentType.JSON)
                .body(equalTo(answerBody))
    }

    /**
     * UserMistake -> 400
     */
    @Test
    fun test_query_UserError() {
        val queryMap = mapOf(
                "type" to gtv("test_query"),
                "a" to gtv("b"),
                "c" to gtv(3)
        )

        val queryString = gtvToJSON(gtv(queryMap), gson)
        val query = GtxQuery("test_query", gtv(mapOf("a" to gtv("b"), "c" to gtv(3), NON_STRICT_QUERY_ARGUMENT to gtv(true))))

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
            .contentType(ContentType.JSON)
            .body(equalTo(answerBody))
    }

    @Test
    fun test_query_when_blockchainRID_too_long_then_400_received() {
        val queryString = """{"a"="b", "c"=3}"""

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .body(queryString)
                .post("/query/${blockchainRID}0000")
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("error", containsString("Blockchain RID"))
    }

    @Test
    fun test_query_when_blockchainRID_too_short_then_400_received() {
        val queryString = """{"a"="b", "c"=3}"""

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .body(queryString)
                .post("/query/1234")
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("error", containsString("Blockchain RID"))
    }

    @Test
    fun test_query_when_blockchainRID_not_hex_then_400_received() {
        val queryString = """{"a"="b", "c"=3}"""

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .body(queryString)
                .post("/query/x8967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("error", containsString("blockchainRid"))
    }

    @Test
    fun `GET query_gtv`() {
        val queryMap = mapOf(
                "type" to gtv("test_query"),
                "a" to gtv("b"),
                "c" to gtv(3)
        )
        val queryString = queryMap.map { "${it.key}=${it.value.toString().trim('"')}" }.joinToString("&")
        val query = GtxQuery("test_query", gtv(mapOf("a" to gtv("b"), "c" to gtv(3), NON_STRICT_QUERY_ARGUMENT to gtv(true))))
        val answer = gtv("answer")

        whenever(model.query(query)).thenReturn(answer)
        whenever(model.queryCacheTtlSeconds).thenReturn(17L)

        restApi.attachModel(blockchainRID, model)

        val body = RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", ContentType.BINARY)
                .get("/query_gtv/${blockchainRID}?$queryString")
                .then()
                .statusCode(200)
                .contentType(ContentType.BINARY)
                .header("Cache-Control", equalTo("public, max-age=17"))
                .header("Expires", equalTo("Thu, 1 Jan 1970 00:00:17 GMT"))

        assertThat(body.extract().response().body.asByteArray()).isContentEqualTo(GtvEncoder.encodeGtv(answer))
    }

    @Test
    fun gtvRequestAndResponseTypes() {
        val query = GtxQuery("test_query", gtv(mapOf("type" to gtv("value"))))
        val answer = gtv("answer")

        whenever(model.query(query)).thenReturn(answer)

        restApi.attachModel(blockchainRID, model)

        val body = RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", ContentType.BINARY)
                .body(query.encode())
                .post("/query_gtv/${blockchainRID}")
                .then()
                .statusCode(200)
                .contentType(ContentType.BINARY)

        assertThat(body.extract().response().body.asByteArray()).isContentEqualTo(GtvEncoder.encodeGtv(answer))
    }

    @Test
    fun `Errors are in GTV format when querying for GTV`() {
        val query = GtxQuery("test_query", gtv(mapOf("arg" to gtv("value"))))

        val errorMessage = "Unknown query"
        whenever(model.query(query)).thenThrow(UserMistake(errorMessage))

        restApi.attachModel(blockchainRID, model)

        val body = RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", ContentType.BINARY)
                .body(query.encode())
                .post("/query_gtv/${blockchainRID}")
                .then()
                .statusCode(400)
                .contentType(ContentType.BINARY)

        assertThat(body.extract().response().body.asByteArray()).isContentEqualTo(GtvEncoder.encodeGtv(gtv(errorMessage)))
    }

    @Test
    fun `400 Bad Request is returned when gtv encoding is incorrect`() {
        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", ContentType.BINARY)
                .body(ByteArray(32))
                .post("/query_gtv/${blockchainRID}")
                .then()
                .statusCode(400)
                .contentType(ContentType.BINARY)
    }

    @Test
    fun `400 Bad Request is returned when gtx encoding is incorrect`() {
        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", ContentType.BINARY)
                .body(GtvEncoder.encodeGtv(gtv("bogus")))
                .post("/query_gtv/${blockchainRID}")
                .then()
                .statusCode(400)
                .contentType(ContentType.BINARY)
    }

    @Test
    fun `JSON response contains null values`() {
        val queryName = "test_query"
        val query = GtxQuery(queryName, gtv(mapOf(NON_STRICT_QUERY_ARGUMENT to gtv(true))))

        val answerString = """{"a":"not-null","b":null}"""
        val answer = gtv(mapOf("a" to gtv("not-null"), "b" to GtvNull))

        whenever(model.query(query)).thenReturn(answer)

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .get("/query/$blockchainRID?type=$queryName")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(equalTo(answerString))
    }
}
