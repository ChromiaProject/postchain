package net.postchain.api.rest.endpoint

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.isContentEqualTo
import io.restassured.RestAssured
import io.restassured.http.ContentType
import net.postchain.api.rest.controller.ExternalModel
import net.postchain.api.rest.controller.HttpExternalModel
import net.postchain.api.rest.controller.RestApi
import net.postchain.common.toHex
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.make_gtv_gson
import net.postchain.gtv.mapper.GtvObjectMapper
import org.hamcrest.core.IsEqual
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RestApiRedirectEndpointTest {
    private val basePath = ""
    private val blockchainRID = "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3"
    private lateinit var restApi: RestApi
    private lateinit var model: ExternalModel

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
        model = HttpExternalModel("http://localhost:${MockPostchainRestApi.port}", 1L)
        restApi = RestApi(0, basePath)
    }

    @AfterEach
    fun tearDown() {
        restApi.stop()
    }

    @Test
    fun `Block at height endpoint can return JSON`() {
        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", RestApi.JSON_CONTENT_TYPE)
                .get("/blocks/$blockchainRID/height/0")
                .then()
                .statusCode(200)
                .contentType(RestApi.JSON_CONTENT_TYPE)
                .body("rid", IsEqual.equalTo(MockPostchainRestApi.block.rid.toHex()))
    }

    @Test
    fun `Block at height endpoint can return GTV`() {
        restApi.attachModel(blockchainRID, model)

        val body = RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", RestApi.OCTET_CONTENT_TYPE)
                .get("/blocks/$blockchainRID/height/0")
                .then()
                .statusCode(200)
                .contentType(RestApi.OCTET_CONTENT_TYPE)

        assertThat(body.extract().response().body.asByteArray()).isContentEqualTo(
                GtvEncoder.encodeGtv(GtvObjectMapper.toGtvDictionary(MockPostchainRestApi.block)))
    }

    @Test
    fun `GTV query can be redirected`() {
        val query = GtvFactory.gtv(listOf(GtvFactory.gtv("test_query"), GtvFactory.gtv(mapOf("arg" to GtvFactory.gtv("value")))))

        restApi.attachModel(blockchainRID, model)

        val body = RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", RestApi.OCTET_CONTENT_TYPE)
                .contentType(ContentType.BINARY)
                .body(GtvEncoder.encodeGtv(query))
                .post("/query_gtv/${blockchainRID}")
                .then()
                .statusCode(200)
                .contentType(RestApi.OCTET_CONTENT_TYPE)

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
                .contentType(RestApi.JSON_CONTENT_TYPE)

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
                .contentType(RestApi.JSON_CONTENT_TYPE)
    }
}