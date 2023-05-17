package net.postchain.api.rest.endpoint

import assertk.assertThat
import assertk.isContentEqualTo
import io.restassured.RestAssured
import io.restassured.http.ContentType
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.core.block.BlockDetail
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.mapper.GtvObjectMapper
import org.hamcrest.core.IsEqual.equalTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RestApiBlockAtHeightEndpointTest {

    private val basePath = "/api/v1"
    private val blockchainRID = "78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3"
    private val block = BlockDetail(
            "34ED10678AAE0414562340E8754A7CCD174B435B52C7F0A4E69470537AEE47E6".hexStringToByteArray(),
            "5AF85874B9CCAC197AA739585449668BE15650C534E08705F6D60A6993FE906D".hexStringToByteArray(),
            "023F9C7FBAFD92E53D7890A61B50B33EC0375FA424D60BD328AA2454408430C383".hexStringToByteArray(),
            0,
            listOf(),
            "03D8844CFC0CE7BECD33CDF49A9881364695C944E266E06356CDA11C2305EAB83A".hexStringToByteArray(),
            0
    )
    private lateinit var restApi: RestApi
    private lateinit var model: Model

    @BeforeEach
    fun setup() {
        model = mock {
            on { chainIID } doReturn 1L
            on { live } doReturn true
        }

        restApi = RestApi(0, basePath)
    }

    @AfterEach
    fun tearDown() {
        restApi.stop()
    }

    @Test
    fun `Block at height endpoint can return JSON`() {
        whenever(model.getBlock(0, true)).thenReturn(block)

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", ContentType.JSON)
                .get("/blocks/$blockchainRID/height/0")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("rid", equalTo(block.rid.toHex()))
    }

    @Test
    fun `Default content type is JSON`() {
        whenever(model.getBlock(0, true)).thenReturn(block)

        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .get("/blocks/$blockchainRID/height/0")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("rid", equalTo(block.rid.toHex()))
    }

    @Test
    fun `Block at height endpoint can return GTV`() {
        whenever(model.getBlock(0, true)).thenReturn(block)

        restApi.attachModel(blockchainRID, model)

        val body = RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", ContentType.BINARY)
                .get("/blocks/$blockchainRID/height/0")
                .then()
                .statusCode(200)
                .contentType(ContentType.BINARY)

        assertThat(body.extract().response().body.asByteArray()).isContentEqualTo(GtvEncoder.encodeGtv(GtvObjectMapper.toGtvDictionary(block)))
    }

    @Test
    fun `Errors are in GTV format when querying for GTV`() {
        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", ContentType.BINARY)
                .get("/blocks/$blockchainRID/height/0")
                .then()
                .statusCode(404)
                .contentType(ContentType.BINARY)
    }
}
