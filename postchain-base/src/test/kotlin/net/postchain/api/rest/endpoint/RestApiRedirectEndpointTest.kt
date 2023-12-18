package net.postchain.api.rest.endpoint

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.isContentEqualTo
import io.restassured.RestAssured
import io.restassured.http.ContentType
import net.postchain.api.rest.controller.ExternalModel
import net.postchain.api.rest.controller.HttpExternalModel
import net.postchain.api.rest.controller.RestApi
import net.postchain.common.BlockchainRid
import net.postchain.common.toHex
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.make_gtv_gson
import net.postchain.gtv.mapper.GtvObjectMapper
import org.hamcrest.core.IsEqual.equalTo
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RestApiRedirectEndpointTest {
    private val basePath = "/api"
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")
    private lateinit var restApi: RestApi
    private lateinit var httpRedirectRestApi: RestApi
    private lateinit var model: ExternalModel
    private lateinit var apisToTest: List<RestApi>

    companion object {
        @BeforeAll
        @JvmStatic
        fun start() {
            MockPostchainRestApi.start()
        }

        @AfterAll
        @JvmStatic
        fun stop() {
            MockPostchainRestApi.close()
        }
    }

    @BeforeEach
    fun setup() {
        model = HttpExternalModel(basePath, "http://localhost:${MockPostchainRestApi.port}", 1L)
        restApi = RestApi(0, basePath, gracefulShutdown = false, clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
        httpRedirectRestApi = RestApi(0, basePath, gracefulShutdown = false, clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), subnodeHttpRedirect = true)
        apisToTest = listOf(restApi, httpRedirectRestApi)
    }

    @AfterEach
    fun tearDown() {
        restApi.close()
    }

    @Test
    fun `Block at height endpoint can return JSON`() {
        apisToTest.forEach {
            it.attachModel(blockchainRID, model)

            RestAssured.given().basePath(basePath).port(it.actualPort())
                    .header("Accept", ContentType.JSON)
                    .get("/blocks/$blockchainRID/height/0")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("rid", equalTo(MockPostchainRestApi.block.rid.toHex()))
        }
    }

    @Test
    fun `Block at height endpoint can return GTV`() {
        apisToTest.forEach {
            it.attachModel(blockchainRID, model)

            val body = RestAssured.given().basePath(basePath).port(it.actualPort())
                    .header("Accept", ContentType.BINARY)
                    .get("/blocks/$blockchainRID/height/0")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.BINARY)

            assertThat(body.extract().response().body.asByteArray()).isContentEqualTo(
                    GtvEncoder.encodeGtv(GtvObjectMapper.toGtvDictionary(MockPostchainRestApi.block)))
        }
    }

    /** REST assured does not follow 307 for POSTs even though it should, we will test separately **/

    @Test
    fun `GTV query can be redirected`() {
        val query = GtvFactory.gtv(listOf(GtvFactory.gtv("test_query"), GtvFactory.gtv(mapOf("arg" to GtvFactory.gtv("value")))))

        restApi.attachModel(blockchainRID, model)

        val body = RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", ContentType.BINARY)
                .contentType(ContentType.BINARY)
                .body(GtvEncoder.encodeGtv(query))
                .post("/query_gtv/${blockchainRID}")
                .then()
                .statusCode(200)
                .contentType(ContentType.BINARY)

        assertThat(body.extract().response().body.asByteArray()).isContentEqualTo(
                GtvEncoder.encodeGtv(MockPostchainRestApi.gtvQueryResponse))
    }

    @Test
    fun `JSON query can be redirected`() {
        val queryString = """["test_query", {"a"="b", "c"=3}]"""

        restApi.attachModel(blockchainRID, model)

        val body = RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .body(queryString)
                .post("/query/$blockchainRID")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)

        assertThat(body.extract().body().asString()).isEqualTo(make_gtv_gson().toJson(MockPostchainRestApi.gtvQueryResponse))
    }

    @Test
    fun `Error will be forwarded`() {
        val queryString = """["error_query", {"a"="b", "c"=3}]"""

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .body(queryString)
                .post("/query/$blockchainRID")
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
    }

    @Test
    fun `POST request is redirected with 307`() {
        val query = GtvFactory.gtv(listOf(GtvFactory.gtv("test_query"), GtvFactory.gtv(mapOf("arg" to GtvFactory.gtv("value")))))

        httpRedirectRestApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(httpRedirectRestApi.actualPort())
                .header("Accept", ContentType.BINARY)
                .contentType(ContentType.BINARY)
                .body(GtvEncoder.encodeGtv(query))
                .post("/query_gtv/${blockchainRID}")
                .then()
                .statusCode(307)
                .header("Location", "http://localhost:${MockPostchainRestApi.port}/query_gtv/${blockchainRID}")
    }
}