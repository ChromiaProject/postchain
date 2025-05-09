// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.endpoint

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.isContentEqualTo
import io.restassured.RestAssured
import io.restassured.http.ContentType
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.common.toHex
import net.postchain.common.wrap
import net.postchain.core.AsyncQueryResponse
import net.postchain.core.AsyncQueryResponseStatus
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.GtvStream
import net.postchain.gtv.gtvToJSON
import net.postchain.gtv.makeStrictGtvGson
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtx.GtxQuery
import net.postchain.gtx.NON_STRICT_QUERY_ARGUMENT
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.core.IsEqual.equalTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.math.BigInteger

/**
 * ProgrammerMistake -> 500
 * "Any standard exception" -> 500
 * UserMistake -> 400
 */
class RestApiQueryEndpointTest {

    private val basePath = "/api/v1"
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")
    private val gson = makeStrictGtvGson()
    private lateinit var restApi: RestApi
    private lateinit var model: Model

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

        val answerString = """{"bi":"92233720368547758079","d":0,"i":17}"""
        val answer = gtv(mapOf("d" to gtv(false), "i" to gtv(17), "bi" to gtv(BigInteger("92233720368547758079"))))

        whenever(model.query(query)).thenReturn(answer)
        whenever(model.queryCacheTtlSeconds).thenReturn(0L)

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .get("/query/$blockchainRID?$queryString")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .header("Cache-Control", nullValue())
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
                .body(equalTo(answerString))
    }

    @Test
    fun test_web_query_with_cache() {
        val queryName = "web_resource"

        val query = GtxQuery(queryName, gtv(mapOf(
                "path" to gtv(listOf()),
                "query_params" to gtv(mapOf())
        )))

        val cacheTtl = 57
        val answerString = "Hello, world!"
        val answer = gtv(mapOf("content_type" to gtv("text/plain"), "content" to gtv(answerString), "cache_ttl_seconds" to gtv(cacheTtl.toLong())))

        whenever(model.query(query)).thenReturn(answer)
        whenever(model.queryCacheTtlSeconds).thenReturn(17L)

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .get("/web_query/$blockchainRID/$queryName")
                .then()
                .statusCode(200)
                .contentType(ContentType.TEXT)
                .header("Cache-Control", equalTo("public, max-age=$cacheTtl"))
                .body(equalTo(answerString))
    }

    @Test
    fun test_web_query_with_byte_array() {
        val queryName = "web_resource"

        val query = GtxQuery(queryName, gtv(mapOf(
                "path" to gtv(listOf()),
                "query_params" to gtv(mapOf())
        )))

        val answerBytes = byteArrayOf(1, 2, 3, 4)
        val answer = gtv(mapOf("content_type" to gtv("application/octet-stream"), "content" to gtv(answerBytes)))

        whenever(model.query(query)).thenReturn(answer)
        whenever(model.queryCacheTtlSeconds).thenReturn(17L)

        restApi.attachModel(blockchainRID, model)

        val body = RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .get("/web_query/$blockchainRID/$queryName")
                .then()
                .statusCode(200)
                .contentType(ContentType.BINARY)
                .header("Cache-Control", equalTo("public, max-age=17"))
                .header("Content-Length", Integer::parseInt, greaterThan(0))
        assertThat(body.extract().response().body.asByteArray()).isContentEqualTo(answerBytes)
    }

    @Test
    fun test_web_query_with_stream_of_unknown_length() {
        val queryName = "web_resource"

        val query = GtxQuery(queryName, gtv(mapOf(
                "path" to gtv(listOf()),
                "query_params" to gtv(mapOf())
        )))

        val answerBytes = byteArrayOf(1, 2, 3, 4)
        val answer = gtv(mapOf("content_type" to gtv("application/octet-stream"),
                "content" to GtvStream(ByteArrayInputStream(answerBytes), null)))

        whenever(model.query(query)).thenReturn(answer)
        whenever(model.queryCacheTtlSeconds).thenReturn(17L)

        restApi.attachModel(blockchainRID, model)

        val body = RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .get("/web_query/$blockchainRID/$queryName")
                .then()
                .statusCode(200)
                .contentType(ContentType.BINARY)
                .header("Cache-Control", equalTo("public, max-age=17"))
        assertThat(body.extract().response().body.asByteArray()).isContentEqualTo(answerBytes)
    }

    @Test
    fun test_web_query_with_stream_of_known_length() {
        val queryName = "web_resource"

        val query = GtxQuery(queryName, gtv(mapOf(
                "path" to gtv(listOf()),
                "query_params" to gtv(mapOf())
        )))

        val answerBytes = byteArrayOf(1, 2, 3, 4)
        val answer = gtv(mapOf("content_type" to gtv("application/octet-stream"),
                "content" to GtvStream(ByteArrayInputStream(answerBytes), answerBytes.size.toLong())))

        whenever(model.query(query)).thenReturn(answer)
        whenever(model.queryCacheTtlSeconds).thenReturn(17L)

        restApi.attachModel(blockchainRID, model)

        val body = RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .get("/web_query/$blockchainRID/$queryName")
                .then()
                .statusCode(200)
                .contentType(ContentType.BINARY)
                .header("Cache-Control", equalTo("public, max-age=17"))
                .header("Content-Length", Integer::parseInt, greaterThan(0))
        assertThat(body.extract().response().body.asByteArray()).isContentEqualTo(answerBytes)
    }

    @Test
    fun test_web_query_with_path() {
        val queryName = "web_resource"

        val query = GtxQuery(queryName, gtv(mapOf(
                "path" to gtv(listOf(gtv("path1"), gtv("path2"))),
                "query_params" to gtv(mapOf())
        )))

        val answerString = "Hello, world!"
        val answer = gtv(mapOf("content_type" to gtv("text/plain"), "content" to gtv(answerString)))

        whenever(model.query(query)).thenReturn(answer)
        whenever(model.queryCacheTtlSeconds).thenReturn(17L)

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .get("/web_query/$blockchainRID/$queryName/path1/path2")
                .then()
                .statusCode(200)
                .contentType(ContentType.TEXT)
                .header("Cache-Control", equalTo("public, max-age=17"))
                .body(equalTo(answerString))
    }

    @Test
    fun test_web_query_with_query() {
        val queryName = "web_resource"

        val query = GtxQuery(queryName, gtv(mapOf(
                "path" to gtv(listOf()),
                "query_params" to gtv(mapOf(
                        "q1" to gtv(gtv("Q1")),
                        "q2" to gtv(gtv("Q2a"), gtv("Q2b")),
                        "q3" to gtv(gtv("")),
                        "q4" to gtv(GtvNull)))
        )))

        val answerString = "Hello, world!"
        val answer = gtv(mapOf("content_type" to gtv("text/plain"), "content" to gtv(answerString)))

        whenever(model.query(query)).thenReturn(answer)
        whenever(model.queryCacheTtlSeconds).thenReturn(17L)

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .param("q1", "Q1")
                .param("q2", "Q2a")
                .param("q2", "Q2b")
                .param("q3", "")
                .param("q4")
                .get("/web_query/$blockchainRID/$queryName")
                .then()
                .statusCode(200)
                .contentType(ContentType.TEXT)
                .header("Cache-Control", equalTo("public, max-age=17"))
                .body(equalTo(answerString))
    }

    @Test
    fun test_web_query_with_path_and_query() {
        val queryName = "web_resource"

        val query = GtxQuery(queryName, gtv(mapOf(
                "path" to gtv(listOf(gtv("path1"), gtv("path2"))),
                "query_params" to gtv(mapOf("q1" to gtv(gtv("Q1")), "q2" to gtv(gtv("Q2a"), gtv("Q2b"))))
        )))

        val answerString = "Hello, world!"
        val answer = gtv(mapOf("content_type" to gtv("text/plain"), "content" to gtv(answerString)))

        whenever(model.query(query)).thenReturn(answer)
        whenever(model.queryCacheTtlSeconds).thenReturn(17L)

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .param("q1", "Q1")
                .param("q2", "Q2a")
                .param("q2", "Q2b")
                .get("/web_query/$blockchainRID/$queryName/path1/path2")
                .then()
                .statusCode(200)
                .contentType(ContentType.TEXT)
                .header("Cache-Control", equalTo("public, max-age=17"))
                .body(equalTo(answerString))
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
        val answerBody = """{"error":"Unknown error"}"""

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

        whenever(model.query(query)).thenThrow(UserMistake(answerMessage))

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
    fun `GET query_gtv with no args`() {
        val queryMap = mapOf(
                "type" to gtv("test_query"),
        )
        val queryString = queryMap.map { "${it.key}=${it.value.toString().trim('"')}" }.joinToString("&")
        val query = GtxQuery("test_query", gtv(mapOf()))
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

        assertThat(body.extract().response().body.asByteArray()).isContentEqualTo(GtvEncoder.encodeGtv(answer))
    }

    @Test
    fun `GET query_gtv with legacy args`() {
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

        assertThat(body.extract().response().body.asByteArray()).isContentEqualTo(GtvEncoder.encodeGtv(answer))
    }

    @Test
    fun `GET query_gtv with GTV args`() {
        val query = GtxQuery("test_query", gtv(mapOf("a" to gtv("b"), "c" to gtv(3))))
        val queryString = "type=${query.name}&~args=${GtvEncoder.encodeGtv(query.args).toHex()}"
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

        assertThat(body.extract().response().body.asByteArray()).isContentEqualTo(GtvEncoder.encodeGtv(answer))
    }

    @Test
    fun `GET query_gtv with invalid GTV args`() {
        val query = GtxQuery("test_query", gtv(mapOf("a" to gtv("b"), "c" to gtv(3))))
        val queryString = "type=${query.name}&~args=${byteArrayOf(1, 2, 3, 4).toHex()}"
        val answer = gtv("answer")

        whenever(model.query(query)).thenReturn(answer)
        whenever(model.queryCacheTtlSeconds).thenReturn(17L)

        restApi.attachModel(blockchainRID, model)

        val body = RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", ContentType.BINARY)
                .get("/query_gtv/${blockchainRID}?$queryString")
                .then()
                .statusCode(400)
                .contentType(ContentType.BINARY)

        assertThat(GtvDecoder.decodeGtv(body.extract().response().body.asByteArray()).asString())
                .isEqualTo("Invalid GTV data")
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

        assertThat(GtvDecoder.decodeGtv(body.extract().response().body.asByteArray()).asString()).isEqualTo(errorMessage)
    }

    @Test
    fun `400 Bad Request is returned when gtv encoding is incorrect`() {
        restApi.attachModel(blockchainRID, model)

        val body = RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", ContentType.BINARY)
                .body(ByteArray(32))
                .post("/query_gtv/${blockchainRID}")
                .then()
                .statusCode(400)
                .contentType(ContentType.BINARY)

        assertThat(GtvDecoder.decodeGtv(body.extract().response().body.asByteArray()).asString())
                .isEqualTo("Invalid GTV data")
    }

    @Test
    fun `400 Bad Request is returned when gtx encoding is incorrect`() {
        restApi.attachModel(blockchainRID, model)

        val body = RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", ContentType.BINARY)
                .body(GtvEncoder.encodeGtv(gtv("bogus")))
                .post("/query_gtv/${blockchainRID}")
                .then()
                .statusCode(400)
                .contentType(ContentType.BINARY)

        assertThat(GtvDecoder.decodeGtv(body.extract().response().body.asByteArray()).asString())
                .isEqualTo("Gtx Query must be an array with 2 elements")
    }

    @Test
    fun `JSON response contains null values`() {
        val queryName = "test_query"
        val query = GtxQuery(queryName, gtv(mapOf()))

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

    @Test
    fun `submit async query`() {
        val query = GtxQuery("test_query", gtv(mapOf("type" to gtv("value"))))

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .body(query.encode())
                .post("/query_async/${blockchainRID}")
                .then()
                .statusCode(202)
                .contentType(ContentType.JSON)

        verify(model).enqueueQuery(query)
    }

    @Test
    fun `fetch async query response`() {
        restApi.attachModel(blockchainRID, model)

        val queryRID = ByteArray(32) { it.toByte() }.wrap()
        val answer = gtv("answer")

        whenever(model.fetchQueryResponse(queryRID)).thenReturn(AsyncQueryResponse(
                status = AsyncQueryResponseStatus.COMPLETED,
                queryResponse = answer,
                errorMessage = null,
        ))

        val body = RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .get("/query_async/${blockchainRID}/${queryRID}")
                .then()
                .statusCode(200)
                .contentType(ContentType.BINARY)

        assertThat(body.extract().response().body.asByteArray()).isContentEqualTo(GtvEncoder.encodeGtv(GtvObjectMapper.toGtvDictionary(AsyncQueryResponse(
                status = AsyncQueryResponseStatus.COMPLETED,
                queryResponse = answer,
                errorMessage = null,
        ))))
    }
}
