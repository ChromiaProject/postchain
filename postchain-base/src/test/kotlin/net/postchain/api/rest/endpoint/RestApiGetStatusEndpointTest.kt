// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.endpoint

import assertk.assertThat
import assertk.isContentEqualTo
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.api.rest.model.ApiStatus
import net.postchain.api.rest.model.TxRid
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.tx.TransactionStatus
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * [GetStatus] and [GetTx] endpoints have common part,
 * so see [RestApiGetTxEndpointTest] for additional tests
 */
class RestApiGetStatusEndpointTest {

    private val basePath = "/api/v1"
    private lateinit var restApi: RestApi
    private lateinit var model: Model
    private val blockchainRID = BlockchainRid.buildFromHex("ABABABABABABABABABABABABABABABABABABABABABABABABABABABABABABABAB")

    private val chainIid = 1L
    private val txHashHex = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

    @BeforeEach
    fun setup() {
        model = mock {
            on { chainIID } doReturn 1L
            on { blockchainRid } doReturn blockchainRID
            on { live } doReturn true
            on { getStatus(TxRid(txHashHex.hexStringToByteArray())) } doReturn ApiStatus(TransactionStatus.CONFIRMED)
        }

        restApi = RestApi(0, basePath, gracefulShutdown = false)
    }

    @AfterEach
    fun tearDown() {
        restApi.close()
    }

    @Test
    fun `getStatus ok`() {
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/tx/$blockchainRID/$txHashHex/status")
                .then()
                .statusCode(200)
                .body("status", equalTo("confirmed"))
    }

    @Test
    fun `getStatus ok via ChainIid`() {
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/tx/iid_${chainIid.toInt()}/$txHashHex/status")
                .then()
                .statusCode(200)
                .body("status", equalTo("confirmed"))
    }

    @Test
    fun `getStatus rejected as JSON`() {
        whenever(model.getStatus(TxRid(txHashHex.hexStringToByteArray()))).thenReturn(
                ApiStatus(TransactionStatus.REJECTED, "Some reason", 1740659274153)
        )
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/tx/$blockchainRID/$txHashHex/status")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("status", equalTo("rejected"))
                .body("rejectReason", equalTo("Some reason"))
                .body("rejectTimestamp", equalTo(1740659274153))
    }

    @Test
    fun `getStatus rejected as GTV`() {
        whenever(model.getStatus(TxRid(txHashHex.hexStringToByteArray()))).thenReturn(
                ApiStatus(TransactionStatus.REJECTED, "Some reason", 1740659274153)
        )
        restApi.attachModel(blockchainRID, model)

        val gtv = gtv(mapOf(
                "status" to gtv("rejected"),
                "rejectReason" to gtv("Some reason"),
                "rejectTimestamp" to gtv(1740659274153)
        ))

        val body = given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", ContentType.BINARY)
                .get("/tx/$blockchainRID/$txHashHex/status")
                .then()
                .statusCode(200)
                .contentType(ContentType.BINARY)

        assertThat(body.extract().response().body.asByteArray()).isContentEqualTo(GtvEncoder.encodeGtv(gtv))
    }
}
