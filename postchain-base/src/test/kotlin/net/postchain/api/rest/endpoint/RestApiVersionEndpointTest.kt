package net.postchain.api.rest.endpoint

import io.restassured.RestAssured
import io.restassured.http.ContentType
import net.postchain.api.rest.controller.RestApi
import net.postchain.api.rest.json.JsonFactory
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.JsonNodeDiagnosticContext
import org.hamcrest.CoreMatchers
import org.hamcrest.core.IsEqual.equalTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RestApiVersionEndpointTest {

    private val basePath = "/api/v1"
    private lateinit var restApi: RestApi
    private val properties = mapOf(
            DiagnosticProperty.VERSION withValue "3.14.15",
            DiagnosticProperty.INFRASTRUCTURE_NAME withValue "base-infra",
            DiagnosticProperty.INFRASTRUCTURE_VERSION withValue "3.14.1592",
            DiagnosticProperty.DATABASE_SERVER_VERSION withValue "16.7",
    )

    private val diagnosticContext = JsonNodeDiagnosticContext(*properties.toList().toTypedArray())

    @BeforeEach
    fun setup() {
        restApi = RestApi(0, basePath, diagnosticContext, false)
    }

    @AfterEach
    fun tearDown() {
        restApi.close()
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

    @Test
    fun infraVersion() {
        val expected = JsonFactory.makePrettyJson().toJson(mapOf(
                "postchain" to properties[DiagnosticProperty.VERSION]?.value?.toString().orEmpty(),
                "infrastructure" to properties[DiagnosticProperty.INFRASTRUCTURE_NAME]?.value?.toString().orEmpty(),
                "infrastructure-version" to properties[DiagnosticProperty.INFRASTRUCTURE_VERSION]?.value?.toString().orEmpty(),
                "rest-api" to RestApi.REST_API_VERSION.toString(),
                "database-server-version" to properties[DiagnosticProperty.DATABASE_SERVER_VERSION]?.value?.toString().orEmpty(),
        ))

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", ContentType.JSON)
                .get("/infrastructure_version")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(CoreMatchers.equalTo(expected))
    }
}
