// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.endpoint

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.restassured.RestAssured
import io.restassured.http.ContentType
import io.restassured.response.ValidatableResponse
import net.postchain.api.rest.BlockSignature
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.base.BaseBlockWitness
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.core.BlockRid
import net.postchain.core.block.BlockDetail
import net.postchain.crypto.Signature
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvNull
import net.postchain.gtv.mapper.GtvObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RestApiConfirmBlockEndpointTest {

    private val basePath = "/api/v1"
    private lateinit var restApi: RestApi
    private lateinit var model: Model
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a1")
    private val signature1 = Signature("0320F0B9E7ECF1A1568C31644B04D37ADC05327F996B9F48220E301DC2FEE6F8FF".hexStringToByteArray(), byteArrayOf(1))
    private val blockSignature1 = BlockSignature.fromSignature(signature1)
    private val signature2 = Signature("0307C88BF37C528B14AF95E421749E72F6DA88790BCE74890BDF780D854D063C40".hexStringToByteArray(), byteArrayOf(2))
    private val witness = BaseBlockWitness.fromSignatures(arrayOf(signature1, signature2))
    private val block = BlockDetail(
            "34ED10678AAE0414562340E8754A7CCD174B435B52C7F0A4E69470537AEE47E6".hexStringToByteArray(),
            "5AF85874B9CCAC197AA739585449668BE15650C534E08705F6D60A6993FE906D".hexStringToByteArray(),
            "023F9C7FBAFD92E53D7890A61B50B33EC0375FA424D60BD328AA2454408430C383".hexStringToByteArray(),
            0,
            listOf(),
            witness.getRawData(),
            0
    )

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
    fun `Confirm block returns signature in GTV`() {
        val res = runQuery(blockSignature1, ContentType.BINARY, 200, ContentType.BINARY)
        assertThat(res.extract().asByteArray()).isEqualTo(
                GtvEncoder.encodeGtv(GtvObjectMapper.toGtvDictionary(BlockSignature.fromSignature(signature1)))
        )
    }

    @Test
    fun `Confirm block returns signature in JSON`() {
        val res = runQuery(blockSignature1, ContentType.JSON, 200, ContentType.JSON)
        assertThat(res.extract().jsonPath().getString("subjectID")).isEqualTo(
                signature1.subjectID.toHex()
        )
    }

    @Test
    fun `Confirm block returns signature in JSON by default`() {
        val res = runQuery(blockSignature1, null, 200, ContentType.JSON)
        assertThat(res.extract().jsonPath().getString("subjectID")).isEqualTo(
                signature1.subjectID.toHex()
        )
    }

    @Test
    fun `Confirm block returns GtvNull for unknown block`() {
        val res = runQuery(null, ContentType.BINARY, 200, ContentType.BINARY)
        assertThat(res.extract().asByteArray()).isEqualTo(
                GtvEncoder.encodeGtv(GtvNull)
        )
    }

    @Test
    fun `Confirm block returns JSON null for unknown block`() {
        val res = runQuery(null, ContentType.JSON, 200, ContentType.JSON)
        assertThat(res.extract().response().asString()).isEqualTo("null")
    }

    @Test
    fun `Confirm block returns signature in JSON by default for unknown block`() {
        val response = runQuery(null, null, 200, ContentType.JSON)
        assertThat(response.extract().response().asString()).isEqualTo("null")
    }

    @Test
    fun `Confirm block throws an error for non-live blockchain`() {
        whenever(model.live).thenReturn(false)
        runQuery(blockSignature1, ContentType.BINARY, 503, ContentType.BINARY)
        runQuery(blockSignature1, ContentType.JSON, 503, ContentType.JSON)
        runQuery(blockSignature1, null, 503, ContentType.JSON)
    }

    private fun runQuery(confirmBlockResult: BlockSignature?, accept: ContentType?, statusCode: Int, contentType: ContentType): ValidatableResponse {
        whenever(model.confirmBlock(BlockRid(block.rid))).thenReturn(confirmBlockResult)

        restApi.attachModel(blockchainRID, model)

        return RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .apply {
                    if (accept != null) header("Accept", accept)
                }
                .get("/blocks/$blockchainRID/confirm/${block.rid.toHex()}")
                .then()
                .statusCode(statusCode)
                .contentType(contentType)
    }
}
