package net.postchain.api.rest.endpoint

import io.restassured.RestAssured
import io.restassured.http.ContentType
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFileReader
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Paths
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class RestApiConfigAtHeightEndpointTest {

    private val basePath = "/api/v1"
    private val blockchainRID = "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3"

    private lateinit var bccFile: File
    private lateinit var bccByteArray: ByteArray
    private lateinit var restApi: RestApi
    private lateinit var model: Model

    @BeforeEach
    fun setup() {
        model = mock {
            on { chainIID } doReturn 1L
            on { live } doReturn true
        }

        bccFile = Paths.get(javaClass.getResource("/net/postchain/config/blockchain_config.xml")!!.toURI()).toFile()
        bccByteArray = GtvEncoder.encodeGtv(GtvFileReader.readFile(bccFile))

        restApi = RestApi(0, basePath)
    }

    @AfterEach
    fun tearDown() {
        restApi.stop()
    }

    @Test
    fun `Configuration at height endpoint with default height can return XML`() {
        whenever(model.getBlockchainConfiguration(-1)).thenReturn(bccByteArray)

        restApi.attachModel(blockchainRID, model)

        val body = RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", "text/xml")
                .get("/config/$blockchainRID")
                .then()
                .statusCode(200)
                .contentType("text/xml")

        assertEquals(bccFile.readText(), body.extract().response().body.asString())
    }

    @Test
    fun `Configuration at height endpoint can return XML`() {
        val height = 42L
        whenever(model.getBlockchainConfiguration(height)).thenReturn(bccByteArray)

        restApi.attachModel(blockchainRID, model)

        val body = RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", "text/xml")
                .queryParam("height", height)
                .get("/config/$blockchainRID")
                .then()
                .statusCode(200)
                .contentType("text/xml")

        assertEquals(bccFile.readText(), body.extract().response().body.asString())
    }

    @Test
    fun `Configuration at height endpoint with default height can return ByteArray`() {
        whenever(model.getBlockchainConfiguration(-1)).thenReturn(bccByteArray)

        restApi.attachModel(blockchainRID, model)

        val body = RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", ContentType.BINARY)
                .get("/config/$blockchainRID")
                .then()
                .statusCode(200)
                .contentType(ContentType.BINARY)

        assertContentEquals(bccByteArray, body.extract().response().body.asByteArray())
    }

    @Test
    fun `Configuration at height endpoint can return ByteArray`() {
        val height = 3L
        whenever(model.getBlockchainConfiguration(height)).thenReturn(bccByteArray)

        restApi.attachModel(blockchainRID, model)

        val body = RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", ContentType.BINARY)
                .queryParam("height", height)
                .get("/config/$blockchainRID")
                .then()
                .statusCode(200)
                .contentType(ContentType.BINARY)

        assertContentEquals(bccByteArray, body.extract().response().body.asByteArray())
    }

}