package net.postchain.api.rest.endpoint

import io.restassured.RestAssured
import io.restassured.http.ContentType
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFileReader
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.startsWith
import org.hamcrest.core.IsEqual.equalTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Paths
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RestApiValidateConfigEndpointTest {

    private val basePath = "/api/v1"
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")

    private lateinit var bccFile: File
    private lateinit var bccByteArray: ByteArray
    private lateinit var restApi: RestApi
    private lateinit var model: Model

    @BeforeEach
    fun setup() {
        model = mock {
            on { chainIID } doReturn 1L
            on { blockchainRid } doReturn blockchainRID
            on { live } doReturn true
        }

        bccFile = Paths.get(javaClass.getResource("/net/postchain/config/blockchain_config.xml")!!.toURI()).toFile()
        bccByteArray = GtvEncoder.encodeGtv(GtvFileReader.readFile(bccFile))

        restApi = RestApi(0, basePath, gracefulShutdown = false, clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
    }

    @AfterEach
    fun tearDown() {
        restApi.close()
    }

    @Test
    fun `Validate configuration endpoint can parse XML`() {
        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Content-Type", "text/xml")
                .body(bccFile.readText())
                .post("/config/$blockchainRID")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(equalTo("{}"))
    }

    @Test
    fun `Validate configuration endpoint can decode GTV`() {
        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Content-Type", "application/octet-stream")
                .body(bccByteArray)
                .post("/config/$blockchainRID")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(equalTo("{}"))
    }

    @Test
    fun `Validate configuration endpoint can return 400 on invalid XML`() {
        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Content-Type", "text/xml")
                .body("no XML")
                .post("/config/$blockchainRID")
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("error", containsString(""))
    }

    @Test
    fun `Validate configuration endpoint can return 400 on invalid GTV`() {
        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Content-Type", "application/octet-stream")
                .body(ByteArray(16))
                .post("/config/$blockchainRID")
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("error", containsString(""))
    }

    @Test
    fun `Validate configuration endpoint can return 400 on invalid configuration`() {
        whenever(model.validateBlockchainConfiguration(any())).thenThrow(UserMistake("invalid configuration"))

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Content-Type", "text/xml")
                .body(bccFile.readText())
                .post("/config/$blockchainRID")
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("error", equalTo("invalid configuration"))
    }

    @Test
    fun `Validate configuration endpoint can return 404 on unknown blockchain RID`() {
        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Content-Type", "text/xml")
                .body(bccFile.readText())
                .post("/config/78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a4")
                .then()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .body("error", startsWith("Can't find blockchain with blockchainRID"))
    }
}
