package net.postchain.api.rest.endpoint

import io.restassured.RestAssured
import io.restassured.http.ContentType
import net.postchain.api.rest.controller.RestApi
import org.hamcrest.core.IsEqual.equalTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RestApiVersionEndpointTest {

    private val basePath = "/api/v1"
    private lateinit var restApi: RestApi

    @BeforeEach
    fun setup() {
        restApi = RestApi(0, basePath)
    }

    @AfterEach
    fun tearDown() {
        restApi.stop()
    }

    @Test
    fun version() {
        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", ContentType.JSON)
                .get("/version")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("version", equalTo(RestApi.REST_API_VERSION))
    }
}
