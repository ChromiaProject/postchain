// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.endpoint

import io.restassured.RestAssured.given
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.api.rest.json.JsonFactory
import net.postchain.common.BlockchainRid
import net.postchain.common.rest.AnchoringChainCheck
import net.postchain.common.rest.HighestBlockHeightAnchoringCheck
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RestApiGetHighestBlockHeightAnchoringCheckEndpointTest {

    private val basePath = "/api/v1"
    private lateinit var restApi: RestApi
    private lateinit var model: Model
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")
    private val gson = JsonFactory.makeJson()

    @BeforeEach
    fun setup() {
        model = mock {
            on { blockchainRid } doReturn blockchainRID
        }
        restApi = RestApi(0, basePath, gracefulShutdown = false, clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
    }

    @AfterEach
    fun tearDown() {
        restApi.close()
    }

    @Test
    fun testGetVerifiedAnchoredBlockHeights() {
        val response = HighestBlockHeightAnchoringCheck(
                cac= AnchoringChainCheck(1000, true),
                sac= AnchoringChainCheck(100, true),
                evm= AnchoringChainCheck(error="error message")
        )

        whenever(
                model.getHighestBlockHeightAnchoringCheckBody(blockchainRID)
        ).thenReturn(response)

        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/highest_block_height_anchoring_check/$blockchainRID")
                .then()
                .statusCode(200)
                .assertThat().body(equalTo(gson.toJson(response).toString()))
    }


}
