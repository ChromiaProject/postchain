package net.postchain.api.rest.endpoint

import io.restassured.RestAssured
import io.restassured.http.ContentType
import net.postchain.api.rest.controller.PostchainModel
import net.postchain.api.rest.controller.RestApi
import net.postchain.common.BlockchainRid
import net.postchain.gtv.GtvDictionary
import net.postchain.gtx.GTXBlockchainConfiguration
import net.postchain.gtx.GTXModule
import net.postchain.gtx.StandardOpsGTXModule
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Test class for the /metadata/{blockchainRid} endpoint.
 */
class RestApiMetadataTest {

    private val basePath = "/api/v1"
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")

    private lateinit var restApi: RestApi

    @BeforeEach
    fun setup() {
        restApi = RestApi(0, basePath, gracefulShutdown = false)
    }

    @AfterEach
    fun tearDown() {
        restApi.close()
    }

    @Test
    fun `Metadata endpoint returns metadata when module implements MetadataProvider`() {
        val gtxBlockchainConfig = mock<GTXBlockchainConfiguration> {
            on { chainID } doReturn 1L
            on { module } doReturn StandardOpsGTXModule()
            on { rawConfig } doReturn GtvDictionary.build(mapOf())
        }

        val postchainModel = PostchainModel(
                gtxBlockchainConfig,
                mock(),
                blockchainRID,
                mock(),
                mock(),
                mock(),
                mock(),
                0L,
                mock()
        )

        restApi.attachModel(blockchainRID, postchainModel)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
            .get("/metadata/$blockchainRID")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("operations.nop.gtxModule", equalTo(StandardOpsGTXModule::class.qualifiedName))
            .body("operations.nop.args[0].name", equalTo("nonce"))
            .body("operations.nop.args[0].required", equalTo(true))
            .body("queries.last_block_info.gtxModule", equalTo(StandardOpsGTXModule::class.qualifiedName))
            .body("queries.last_block_info.returnType.gtvTypes[0]", equalTo("DICT"))
    }

    @Test
    fun `Metadata endpoint returns empty metadata when module does not implement MetadataProvider`() {
        val nonMetadataProviderModule = mock<GTXModule>()

        val gtxBlockchainConfig = mock<GTXBlockchainConfiguration> {
            on { chainID } doReturn 1L
            on { module } doReturn nonMetadataProviderModule
            on { rawConfig } doReturn GtvDictionary.build(mapOf())
        }

        val postchainModel = PostchainModel(
                gtxBlockchainConfig,
                mock(),
                blockchainRID,
                mock(),
                mock(),
                mock(),
                mock(),
                0L,
                mock()
        )

        restApi.attachModel(blockchainRID, postchainModel)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
            .get("/metadata/$blockchainRID")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
    }

    @Test
    fun `Metadata endpoint returns 404 when blockchain not found`() {
        val nonExistentBlockchainRid = BlockchainRid.buildFromHex("1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
            .get("/metadata/$nonExistentBlockchainRid")
            .then()
            .statusCode(404)
            .contentType(ContentType.JSON)
            .body("error", equalTo("Can't find blockchain with blockchainRID: $nonExistentBlockchainRid"))
    }
}
