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
import net.postchain.crypto.KeyPair
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.Signature
import net.postchain.crypto.devtools.KeyPairHelper.privKey
import net.postchain.crypto.devtools.KeyPairHelper.pubKey
import net.postchain.gtv.GtvDecoder
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

    // keys
    private val cs = Secp256K1CryptoSystem()
    private val keyPair0 = KeyPair(pubKey(0), privKey(0))
    private val sigMaker0 = cs.buildSigMaker(keyPair0)
    private val keyPair1 = KeyPair(pubKey(1), privKey(1))
    private val sigMaker1 = cs.buildSigMaker(keyPair1)

    // block
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a1")
    private val blockRid = BlockRid.buildFromHex("34ED10678AAE0414562340E8754A7CCD174B435B52C7F0A4E69470537AEE47E6")

    // signatures
    private val signature0 = sigMaker0.signMessage(blockRid.data)
    private val blockSignature0 = BlockSignature.fromSignature(signature0)
    private val signature1 = sigMaker1.signMessage(blockRid.data)
    private val blockSignature1 = BlockSignature.fromSignature(signature1)
    private val witness = BaseBlockWitness.fromSignatures(arrayOf(signature0, signature1))

    // BlockDetail
    private val block = BlockDetail(
            rid = blockRid.data,
            prevBlockRID = "5AF85874B9CCAC197AA739585449668BE15650C534E08705F6D60A6993FE906D".hexStringToByteArray(),
            header = "023F9C7FBAFD92E53D7890A61B50B33EC0375FA424D60BD328AA2454408430C383".hexStringToByteArray(),
            height = 0,
            transactions = listOf(),
            witness = witness.getRawData(),
            timestamp = 0
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
        val rawSignature = runQuery(
                blockSignature0, ContentType.BINARY, 200, ContentType.BINARY
        ).extract().asByteArray()
        assertThat(rawSignature).isEqualTo(
                GtvEncoder.encodeGtv(GtvObjectMapper.toGtvDictionary(BlockSignature.fromSignature(signature0)))
        )

        val blockSignature = GtvObjectMapper.fromGtv(GtvDecoder.decodeGtv(rawSignature), BlockSignature::class)
        // asserting that subjectID is what we sent a confirmation request to
        assertThat(blockSignature.subjectID).isEqualTo(signature0.subjectID)
        // verifying signature
        assertThat(cs.makeVerifier()(block.rid, blockSignature.toSignature())).isEqualTo(true)
    }

    @Test
    fun `Confirm block returns signature in JSON`() {
        val blockSignatureJsonPath = runQuery(
                blockSignature1, ContentType.JSON, 200, ContentType.JSON
        ).extract().jsonPath()

        // asserting that json has only two keys
        assertThat(
                blockSignatureJsonPath.getMap<String, String>(".").keys
        ).isEqualTo(setOf("subjectID", "data"))

        // asserting that subjectID is what we sent a confirmation request to
        assertThat(
                blockSignatureJsonPath.getString("subjectID").hexStringToByteArray()
        ).isEqualTo(signature1.subjectID)

        // verifying signature
        val signature = Signature(
                signature1.subjectID,
                blockSignatureJsonPath.getString("data").hexStringToByteArray())
        assertThat(cs.makeVerifier()(block.rid, signature)).isEqualTo(true)
    }

    @Test
    fun `Confirm block returns signature in JSON by default`() {
        val res = runQuery(blockSignature0, null, 200, ContentType.JSON)
        assertThat(res.extract().jsonPath().getString("subjectID")).isEqualTo(
                signature0.subjectID.toHex()
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
        runQuery(blockSignature0, ContentType.BINARY, 503, ContentType.BINARY)
        runQuery(blockSignature0, ContentType.JSON, 503, ContentType.JSON)
        runQuery(blockSignature0, null, 503, ContentType.JSON)
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
