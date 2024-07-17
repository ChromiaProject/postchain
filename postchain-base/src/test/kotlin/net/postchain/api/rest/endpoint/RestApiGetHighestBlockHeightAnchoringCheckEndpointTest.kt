// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.endpoint

import io.restassured.RestAssured.given
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.api.rest.json.JsonFactory
import net.postchain.common.BlockchainRid
import net.postchain.common.rest.AnchoringChainCheck
import net.postchain.common.rest.HighestBlockHeightAnchoringCheck
import net.postchain.debug.DiagnosticProperty
import net.postchain.debug.EagerDiagnosticValue
import net.postchain.debug.NodeDiagnosticContext
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RestApiGetHighestBlockHeightAnchoringCheckEndpointTest {

    private val basePath = "/api/v1"
    private lateinit var restApi: RestApi
    private lateinit var model: Model
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")
    private val gson = JsonFactory.makeJson()
    private val nodeDiagnosticContextMock = mock<NodeDiagnosticContext> {
        on { get(DiagnosticProperty.BLOCKCHAIN_HIGHEST_BLOCK_HEIGHT_CLUSTER_ANCHORING_CHECK) } doReturn EagerDiagnosticValue(mutableMapOf(blockchainRID to AnchoringChainCheck(1000L, true)))
        on { get(DiagnosticProperty.BLOCKCHAIN_HIGHEST_BLOCK_HEIGHT_SYSTEM_ANCHORING_CHECK) } doReturn EagerDiagnosticValue(mutableMapOf(blockchainRID to AnchoringChainCheck(100L, true)))
        on { get(DiagnosticProperty.BLOCKCHAIN_HIGHEST_BLOCK_HEIGHT_EVM_ANCHORING_CHECK) } doReturn EagerDiagnosticValue(mutableMapOf(blockchainRID to AnchoringChainCheck(error = "Error message")))
    }

    @BeforeEach
    fun setup() {
        model = mock {
            on { blockchainRid } doReturn blockchainRID
        }
        restApi = RestApi(0, basePath, nodeDiagnosticContext = nodeDiagnosticContextMock, gracefulShutdown = false, clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
    }

    @AfterEach
    fun tearDown() {
        restApi.close()
    }

    @Test
    fun testGetVerifiedAnchoredBlockHeights() {
        val expected = HighestBlockHeightAnchoringCheck(
                cac = AnchoringChainCheck(1000, true, null),
                sac = AnchoringChainCheck(100, true, null),
                evm = AnchoringChainCheck(error = "Error message")
        )

        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/highest_block_height_anchoring_check/$blockchainRID")
                .then()
                .statusCode(200)
                .assertThat().body(equalTo(gson.toJson(expected).toString()))
    }
}
