package net.postchain.api.rest.endpoint

import io.restassured.RestAssured
import io.restassured.http.ContentType
import net.postchain.api.rest.InfraVersion
import net.postchain.api.rest.Version
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.api.rest.controller.RestApi.Companion.REST_API_VERSION
import net.postchain.api.rest.json.JsonFactory
import net.postchain.common.BlockchainRid
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.JsonNodeDiagnosticContext
import org.hamcrest.CoreMatchers
import org.hamcrest.core.IsEqual.equalTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RestApiVersionEndpointTest {

    private val basePath = "/api/v1"
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")
    private lateinit var restApi: RestApi
    private lateinit var model: Model

    private val properties = mapOf(
            DiagnosticProperty.VERSION withValue "3.14.15",
            DiagnosticProperty.INFRASTRUCTURE_NAME withValue "base-infra",
            DiagnosticProperty.INFRASTRUCTURE_VERSION withValue "3.14.1592",
            DiagnosticProperty.DATABASE_SERVER_VERSION withValue "16.7",
    )

    private val diagnosticContext = JsonNodeDiagnosticContext(*properties.toList().toTypedArray())

    @BeforeEach
    fun setup() {
        model = mock {
            on { chainIID } doReturn 1L
            on { blockchainRid } doReturn blockchainRID
            on { live } doReturn true
        }
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
                .body("version", equalTo(REST_API_VERSION))
    }

    @Test
    fun `version for blockchain`() {
        whenever(model.getVersion()).thenReturn(Version(REST_API_VERSION))

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", ContentType.JSON)
                .get("/version/$blockchainRID")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("version", equalTo(REST_API_VERSION))
    }

    @Test
    fun infraVersion() {
        val expected = JsonFactory.makePrettyJson().toJson(mapOf(
                "postchain" to properties[DiagnosticProperty.VERSION]?.value?.toString().orEmpty(),
                "infrastructure" to properties[DiagnosticProperty.INFRASTRUCTURE_NAME]?.value?.toString().orEmpty(),
                "infrastructure-version" to properties[DiagnosticProperty.INFRASTRUCTURE_VERSION]?.value?.toString().orEmpty(),
                "rest-api" to REST_API_VERSION.toString(),
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

    @Test
    fun `infraVersion for blockchain`() {
        whenever(model.getInfrastructureVersion()).thenReturn(InfraVersion(
                postchain = "1.2.3",
                infrastructure = "foo",
                infrastructureVersion = "2.3.4",
                restApi = REST_API_VERSION.toString(),
                databaseServerVersion = "16.7"
        ))

        restApi.attachModel(blockchainRID, model)

        val expected = JsonFactory.makePrettyJson().toJson(mapOf(
                "postchain" to "1.2.3",
                "infrastructure" to "foo",
                "infrastructure-version" to "2.3.4",
                "rest-api" to REST_API_VERSION.toString(),
                "database-server-version" to "16.7",
        ))

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", ContentType.JSON)
                .get("/infrastructure_version/$blockchainRID")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(CoreMatchers.equalTo(expected))
    }
}
