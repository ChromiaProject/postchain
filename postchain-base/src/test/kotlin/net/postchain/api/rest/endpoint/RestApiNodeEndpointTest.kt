// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.endpoint

import io.restassured.RestAssured.given
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.api.rest.json.JsonFactory
import net.postchain.common.BlockchainRid
import net.postchain.debug.DpNodeType
import net.postchain.ebft.NodeBlockState
import net.postchain.ebft.rest.contract.StateNodeStatus
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

class RestApiNodeEndpointTest {

    private val basePath = "/api/v1"
    private lateinit var restApi: RestApi
    private lateinit var model: Model
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")
    private val gson = JsonFactory.makeJson()

    @BeforeEach
    fun setup() {
        model = mock {
            on { chainIID } doReturn 1L
            on { blockchainRid } doReturn blockchainRID
            on { live } doReturn true
        }

        restApi = RestApi(0, basePath, gracefulShutdown = false, clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
    }

    @AfterEach
    fun tearDown() {
        restApi.close()
    }

    @Test
    fun testGetMyNodeStatus() {
        val response = StateNodeStatus(
                pubKey = "",
                type = DpNodeType.NODE_TYPE_VALIDATOR.name,
                height = 233,
                serial = 41744989480,
                state = NodeBlockState.WaitBlock.name,
                round = 0,
                revolting = false,
                blockRid = null
        )

        whenever(
                model.nodeStatusQuery()
        ).thenReturn(response)

        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/node/$blockchainRID/my_status")
                .then()
                .statusCode(200)
                .assertThat().body(equalTo(gson.toJson(response).toString()))
    }

    @Test
    fun testGetNodeStatuses() {
        val response =
                listOf(
                        StateNodeStatus(
                                pubKey = "",
                                type = DpNodeType.NODE_TYPE_VALIDATOR.name,
                                height = 233,
                                serial = 41744989480,
                                state = NodeBlockState.WaitBlock.name,
                                round = 0,
                                revolting = false,
                                blockRid = null
                        ),
                        StateNodeStatus(
                                pubKey = "",
                                type = DpNodeType.NODE_TYPE_VALIDATOR.name,
                                height = 233,
                                serial = 41744999981,
                                state = NodeBlockState.WaitBlock.name,
                                round = 0,
                                revolting = false,
                                blockRid = null
                        ))

        whenever(
                model.nodePeersStatusQuery()
        ).thenReturn(
                response
        )

        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/node/$blockchainRID/statuses")
                .then()
                .statusCode(200)
                .assertThat().body(equalTo(gson.toJson(response).toString()))
    }
}
