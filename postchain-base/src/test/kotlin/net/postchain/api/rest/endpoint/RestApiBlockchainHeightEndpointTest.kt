package net.postchain.api.rest.endpoint

import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import net.postchain.api.rest.BlockHeight
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.common.BlockchainRid
import org.hamcrest.core.IsEqual.equalTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RestApiBlockchainHeightEndpointTest {

    private val basePath = "/api/v1"
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")

    private lateinit var restApi: RestApi
    private lateinit var model: Model

    @BeforeEach
    fun setup() {
        model = mock {
            on { chainIID } doReturn 1L
            on { blockchainRid } doReturn blockchainRID
            on { live } doReturn true
        }

        restApi = RestApi(0, basePath, gracefulShutdown = false)
    }

    @AfterEach
    fun tearDown() {
        restApi.close()
    }

    @Test
    fun blockchainHeight() {
        val blockHeight = BlockHeight(17)
        whenever(model.getCurrentBlockHeight()).thenReturn(blockHeight)

        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/blockchain/$blockchainRID/height")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("blockHeight", equalTo(17))
    }
}
