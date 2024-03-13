// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.endpoint

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.restassured.RestAssured
import io.restassured.http.ContentType
import io.restassured.response.ValidatableResponse
import net.postchain.PostchainContext
import net.postchain.api.rest.BlockSignature
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.PostchainModel
import net.postchain.api.rest.controller.RestApi
import net.postchain.base.BaseBlockHeader
import net.postchain.base.BaseBlockWitness
import net.postchain.base.BaseBlockWitnessBuilder
import net.postchain.base.configuration.BaseBlockchainConfiguration
import net.postchain.base.cryptoSystem
import net.postchain.base.data.BaseBlockWitnessProvider
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.core.BlockRid
import net.postchain.core.block.BlockDetail
import net.postchain.core.block.BlockQueries
import net.postchain.core.block.InitialBlockData
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.KeyPair
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.SigMaker
import net.postchain.crypto.Signature
import net.postchain.crypto.devtools.KeyPairHelper.privKey
import net.postchain.crypto.devtools.KeyPairHelper.pubKey
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvNull
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class RestApiConfirmBlockEndpointTest {

    private val basePath = "/api/v1"
    private lateinit var restApi: RestApi
    private lateinit var model0: Model
    private lateinit var model1: Model

    // keys
    private val cs = Secp256K1CryptoSystem()
    private val keyPair0 = KeyPair(pubKey(0), privKey(0))
    private val sigMaker0 = cs.buildSigMaker(keyPair0)
    private val keyPair1 = KeyPair(pubKey(1), privKey(1))
    private val sigMaker1 = cs.buildSigMaker(keyPair1)
    private val subjects = arrayOf(keyPair0.pubKey.data, keyPair1.pubKey.data)

    // block
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a1")
    private val prevBlockRID = BlockRid.buildFromHex("5AF85874B9CCAC197AA739585449668BE15650C534E08705F6D60A6993FE906D")

    // block header
    val blockHeader = BaseBlockHeader.make(
            merkleHashCalculator = GtvMerkleHashCalculator(cryptoSystem),
            iBlockData = InitialBlockData(
                    blockchainRid = blockchainRID,
                    blockIID = 0,
                    chainID = 1L,
                    prevBlockRID = prevBlockRID.data,
                    height = 0,
                    timestamp = 0L,
                    blockHeightDependencyArr = emptyArray()
            ),
            rootHash = ByteArray(32) { 0 },
            timestamp = 0L,
            extraData = emptyMap()
    )

    // signatures
    private val signature0 = signBlock(blockHeader, cs, sigMaker0, subjects)
    private val signature1 = signBlock(blockHeader, cs, sigMaker1, subjects)
    private val witness = BaseBlockWitness.fromSignatures(arrayOf(signature0, signature1))

    // BlockDetail
    private val block = BlockDetail(
            rid = blockHeader.blockRID,
            prevBlockRID = blockHeader.prevBlockRID,
            header = blockHeader.rawData,
            height = 0,
            transactions = listOf(),
            witness = witness.getRawData(),
            timestamp = 0
    )

    private val unknownBlock = BlockRid.buildRepeat(0)

    companion object {
        fun signBlock(blockHeader: BaseBlockHeader, cryptoSystem: CryptoSystem, sigMaker: SigMaker, subjects: Array<ByteArray>): Signature {
            val witnessBuilder = BaseBlockWitnessProvider(cryptoSystem, sigMaker, subjects)
                    .createWitnessBuilderWithOwnSignature(blockHeader) as BaseBlockWitnessBuilder
            return witnessBuilder.getMySignature()
        }
    }

    @BeforeEach
    fun setup() {
        val bcConfig0: BaseBlockchainConfiguration = mock {
            on { chainID } doReturn 1L
            on { blockSigMaker } doReturn sigMaker0
            on { signers } doReturn listOf(keyPair0.pubKey.data, keyPair1.pubKey.data)
            on { rawConfig } doReturn GtvNull
        }

        val bcConfig1: BaseBlockchainConfiguration = mock {
            on { chainID } doReturn 1L
            on { blockSigMaker } doReturn sigMaker1
            on { signers } doReturn listOf(keyPair0.pubKey.data, keyPair1.pubKey.data)
            on { rawConfig } doReturn GtvNull
        }

        val blockQueries: BlockQueries = mock {
            val blockResult: CompletionStage<BlockDetail?> = CompletableFuture.completedStage(block)
            on { getBlock(eq(block.rid), any()) } doReturn blockResult
            val unknownBlockResult: CompletionStage<BlockDetail?> = CompletableFuture.completedStage(null)
            on { getBlock(eq(unknownBlock.data), any()) } doReturn unknownBlockResult
        }

        val postchainContext: PostchainContext = mock {
            on { cryptoSystem } doReturn cs
        }

        model0 = spy(PostchainModel(
                bcConfig0, mock(), blockQueries, blockchainRID, mock(), postchainContext, mock(), 0L
        ))

        model1 = spy(PostchainModel(
                bcConfig1, mock(), blockQueries, blockchainRID, mock(), postchainContext, mock(), 0L
        ))

        restApi = RestApi(0, basePath, gracefulShutdown = false, clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
    }

    @AfterEach
    fun tearDown() {
        restApi.close()
    }

    @Test
    fun `Confirm block returns signature in GTV`() {
        val rawSignature = runQuery(model0, block.rid, ContentType.BINARY, 200, ContentType.BINARY).extract().asByteArray()
        assertThat(rawSignature).isEqualTo(
                GtvEncoder.encodeGtv(GtvObjectMapper.toGtvDictionary(BlockSignature.fromSignature(signature0)))
        )

        val blockSignature = GtvObjectMapper.fromGtv(GtvDecoder.decodeGtv(rawSignature), BlockSignature::class)
        // asserting that subjectID is what we sent a confirmation request to
        assertThat(blockSignature.subjectID).isEqualTo(signature0.subjectID)
        // verifying signature
        assertThat(cs.verifyDigest(block.rid, blockSignature.toSignature())).isEqualTo(true)
    }

    @Test
    fun `Confirm block returns signature in JSON`() {
        val blockSignatureJsonPath = runQuery(model1, block.rid, ContentType.JSON, 200, ContentType.JSON).extract().jsonPath()

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
        assertThat(cs.verifyDigest(block.rid, signature)).isEqualTo(true)
    }

    @Test
    fun `Confirm block returns signature in JSON by default`() {
        val res = runQuery(model0, block.rid, null, 200, ContentType.JSON)
        assertThat(res.extract().jsonPath().getString("subjectID")).isEqualTo(
                signature0.subjectID.toHex()
        )
    }

    @Test
    fun `Confirm block returns GtvNull for unknown block`() {
        val res = runQuery(model0, unknownBlock.data, ContentType.BINARY, 200, ContentType.BINARY)
        assertThat(res.extract().asByteArray()).isEqualTo(
                GtvEncoder.encodeGtv(GtvNull)
        )
    }

    @Test
    fun `Confirm block returns JSON null for unknown block`() {
        val res = runQuery(model0, unknownBlock.data, ContentType.JSON, 200, ContentType.JSON)
        assertThat(res.extract().response().asString()).isEqualTo("null")
    }

    @Test
    fun `Confirm block returns signature in JSON by default for unknown block`() {
        val response = runQuery(model0, unknownBlock.data, null, 200, ContentType.JSON)
        assertThat(response.extract().response().asString()).isEqualTo("null")
    }

    @Test
    fun `Confirm block throws an error for non-live blockchain`() {
        whenever(model0.live).thenReturn(false)
        runQuery(model0, block.rid, ContentType.BINARY, 503, ContentType.BINARY)
        runQuery(model0, block.rid, ContentType.JSON, 503, ContentType.JSON)
        runQuery(model0, block.rid, null, 503, ContentType.JSON)
    }

    private fun runMockedQuery(confirmBlockResult: BlockSignature?, accept: ContentType?, statusCode: Int, contentType: ContentType): ValidatableResponse {
        whenever(model0.confirmBlock(BlockRid(block.rid))).thenReturn(confirmBlockResult)

        restApi.attachModel(blockchainRID, model0)

        return RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .apply {
                    if (accept != null) header("Accept", accept)
                }
                .get("/blocks/$blockchainRID/confirm/${block.rid.toHex()}")
                .then()
                .statusCode(statusCode)
                .contentType(contentType)
    }

    private fun runQuery(model: Model, blockRid: ByteArray, accept: ContentType?, statusCode: Int, contentType: ContentType): ValidatableResponse {
        restApi.attachModel(blockchainRID, model)
        return RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .apply {
                    if (accept != null) header("Accept", accept)
                }
                .get("/blocks/$blockchainRID/confirm/${blockRid.toHex()}")
                .then()
                .statusCode(statusCode)
                .contentType(contentType)
    }
}
