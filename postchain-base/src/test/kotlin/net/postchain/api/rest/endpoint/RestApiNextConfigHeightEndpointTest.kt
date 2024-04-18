package net.postchain.api.rest.endpoint

import io.restassured.RestAssured
import net.postchain.api.rest.BlockHeight
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.common.BlockchainRid
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFileReader
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

class RestApiNextConfigHeightEndpointTest {

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
    fun `Next config height accepts height arg equals -1`() {
        whenever(model.getNextBlockchainConfigurationHeight(any()))
                .thenReturn(BlockHeight(0L))

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .queryParam("height", -1)
                .get("/config/$blockchainRID/next_height")
                .then()
                .statusCode(200)
                .contentType("application/json")
    }

    @Test
    fun `Next config height can return height in JSON`() {
        whenever(model.getNextBlockchainConfigurationHeight(any()))
                .thenReturn(BlockHeight(123L))

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", "application/json")
                .queryParam("height", 0)
                .get("/config/$blockchainRID/next_height")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("blockHeight", equalTo(123))
    }

    @Test
    fun `Next config height can return height in JSON by default`() {
        whenever(model.getNextBlockchainConfigurationHeight(any()))
                .thenReturn(BlockHeight(123L))

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .queryParam("height", 0)
                .get("/config/$blockchainRID/next_height")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("blockHeight", equalTo(123))
    }

    @Test
    fun `Next config height can return null in JSON`() {
        whenever(model.getNextBlockchainConfigurationHeight(any()))
                .thenReturn(null)

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", "application/json")
                .queryParam("height", 0)
                .get("/config/$blockchainRID/next_height")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body(equalTo("null"))
    }

    @Test
    fun `Next config height can return 404 on unknown blockchain RID`() {
        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", "application/json")
                .queryParam("height", 0)
                .get("/config/${BlockchainRid.ZERO_RID}/next_height")
                .then()
                .statusCode(404)
                .contentType("application/json")
                .body("error", startsWith("Can't find blockchain with blockchainRID"))
    }

    @Test
    fun `Next config height throws an error for non-live blockchain`() {
        whenever(model.live).thenReturn(false)

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", "application/json")
                .queryParam("height", 0)
                .get("/config/$blockchainRID/next_height")
                .then()
                .statusCode(503)
                .contentType("application/json")
                .body("error", startsWith("Blockchain is unavailable"))
    }
}
