// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.endpoint

import assertk.assertThat
import assertk.isContentEqualTo
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.api.rest.json.JsonFactory
import net.postchain.base.BaseBlockWitness
import net.postchain.base.cryptoSystem
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.core.BlockRid
import net.postchain.core.TxDetail
import net.postchain.core.block.BlockDetail
import net.postchain.crypto.Signature
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvNull
import net.postchain.gtv.mapper.GtvObjectMapper
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

class RestApiGetBlockEndpointTest {
    private val basePath = "/api/v1"
    private lateinit var restApi: RestApi
    private lateinit var model: Model
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a1")
    private val blockchainRID2 = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")
    private val gson = JsonFactory.makeJson()
    private val witness = BaseBlockWitness.fromSignatures(arrayOf(
            Signature("0320F0B9E7ECF1A1568C31644B04D37ADC05327F996B9F48220E301DC2FEE6F8FF".hexStringToByteArray(), ByteArray(0)),
            Signature("0307C88BF37C528B14AF95E421749E72F6DA88790BCE74890BDF780D854D063C40".hexStringToByteArray(), ByteArray(0))
    ))
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
    fun testGetAllBlocks() {
        val response = listOf(
                BlockDetail(
                        "blockRid001".toByteArray(),
                        blockchainRID2.data,
                        "some header".toByteArray(),
                        0,
                        listOf(),
                        witness.getRawData(),
                        1574849700),
                BlockDetail(
                        "blockRid002".toByteArray(),
                        "blockRid001".toByteArray(),
                        "some other header".toByteArray(),
                        1,
                        listOf(TxDetail("tx1".toByteArray(), "tx1".toByteArray(), "tx1".toByteArray())),
                        witness.getRawData(),
                        1574849760),
                BlockDetail(
                        "blockRid003".toByteArray(),
                        "blockRid002".toByteArray(),
                        "yet another header".toByteArray(),
                        2, listOf(),
                        witness.getRawData(),
                        1574849880),
                BlockDetail(
                        "blockRid004".toByteArray(),
                        "blockRid003".toByteArray(),
                        "guess what? Another header".toByteArray(),
                        3,
                        listOf(
                                TxDetail("tx2".toByteArray(), "tx2".toByteArray(), "tx2".toByteArray()),
                                TxDetail("tx3".toByteArray(), "tx3".toByteArray(), "tx3".toByteArray()),
                                TxDetail("tx4".toByteArray(), "tx4".toByteArray(), "tx4".toByteArray())
                        ),
                        witness.getRawData(),
                        1574849940)
        )

        whenever(
                model.getBlocks(Long.MAX_VALUE, 25, false)
        ).thenReturn(response)

        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/blocks/$blockchainRID?before-time=${Long.MAX_VALUE}&limit=${25}&txs=true")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(equalTo(gson.toJson(response).toString()))
    }

    @Test
    fun testGetTwoLastBlocks_txHashesOnly() {
        val response = listOf(
                BlockDetail(
                        "blockRid003".toByteArray(),
                        "blockRid002".toByteArray(),
                        "yet another header".toByteArray(),
                        2,
                        listOf(),
                        witness.getRawData(),
                        1574849880),
                BlockDetail(
                        "blockRid004".toByteArray(),
                        "blockRid003".toByteArray(),
                        "guess what? Another header".toByteArray(),
                        3,
                        listOf(
                                TxDetail("hash2".toByteArray(), "tx2RID".toByteArray(), null),
                                TxDetail("hash3".toByteArray(), "tx3RID".toByteArray(), null),
                                TxDetail("hash4".toByteArray(), "tx4RID".toByteArray(), null)
                        ),
                        witness.getRawData(),
                        1574849940)
        )

        whenever(
                model.getBlocks(1574849940, 2, true)
        ).thenReturn(response)

        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/blocks/$blockchainRID?before-time=${1574849940}&limit=${2}&txs=false")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(equalTo(gson.toJson(response).toString()))
    }

    @Test
    fun testGetTwoLastBlocksBeforeHeight_txHashesOnly() {
        val response = listOf(
                BlockDetail(
                        "blockRid003".toByteArray(),
                        "blockRid002".toByteArray(),
                        "yet another header".toByteArray(),
                        2,
                        listOf(),
                        witness.getRawData(),
                        1574849880),
                BlockDetail(
                        "blockRid004".toByteArray(),
                        "blockRid003".toByteArray(),
                        "guess what? Another header".toByteArray(),
                        3,
                        listOf(
                                TxDetail("hash2".toByteArray(), "tx2RID".toByteArray(), null),
                                TxDetail("hash3".toByteArray(), "tx3RID".toByteArray(), null),
                                TxDetail("hash4".toByteArray(), "tx4RID".toByteArray(), null)
                        ),
                        witness.getRawData(),
                        1574849940)
        )

        whenever(
                model.getBlocksBeforeHeight(4, 2, true)
        ).thenReturn(response)

        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/blocks/$blockchainRID?before-height=${4}&limit=${2}&txs=false")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(equalTo(gson.toJson(response).toString()))
    }

    @Test
    fun testGetBlocksWithoutParams() {
        val blocks = listOf(
                BlockDetail("blockRid001".toByteArray(), blockchainRID2.data, "some header".toByteArray(), 0, listOf(), witness.getRawData(), 1574849700),
                BlockDetail(
                        "blockRid002".toByteArray(),
                        "blockRid001".toByteArray(),
                        "some other header".toByteArray(),
                        1,
                        listOf(
                                TxDetail(
                                        cryptoSystem.digest("tx1".toByteArray()),
                                        "tx1 - 001".toByteArray().slice(IntRange(0, 4)).toByteArray(),
                                        "tx1".toByteArray()
                                )
                        ),
                        witness.getRawData(),
                        1574849760
                ),
                BlockDetail(
                        "blockRid003".toByteArray(),
                        "blockRid002".toByteArray(),
                        "yet another header".toByteArray(),
                        2,
                        listOf(),
                        witness.getRawData(),
                        1574849880
                ),
                BlockDetail(
                        "blockRid004".toByteArray(),
                        "blockRid003".toByteArray(),
                        "guess what? Another header".toByteArray(),
                        3,
                        listOf(
                                TxDetail(
                                        cryptoSystem.digest("tx2".toByteArray()),
                                        "tx2 - 002".toByteArray().slice(IntRange(0, 4)).toByteArray(),
                                        "tx2".toByteArray()
                                ),
                                TxDetail(
                                        cryptoSystem.digest("tx3".toByteArray()),
                                        "tx3 - 003".toByteArray().slice(IntRange(0, 4)).toByteArray(),
                                        "tx3".toByteArray()
                                ),
                                TxDetail(
                                        cryptoSystem.digest("tx4".toByteArray()),
                                        "tx4 - 004".toByteArray().slice(IntRange(0, 4)).toByteArray(),
                                        "tx4".toByteArray()
                                )
                        ),
                        witness.getRawData(),
                        1574849940
                )
        )

        whenever(
                model.getBlocks(Long.MAX_VALUE, 25, true)
        ).thenReturn(blocks)

        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/blocks/$blockchainRID")
                .then()
                .contentType(ContentType.JSON)
                .statusCode(200)
    }

    @Test
    fun testGetBlockByRID() {
        whenever(
                model.getBlock(BlockRid(block.rid), true)
        ).thenReturn(block)
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/blocks/$blockchainRID/${block.rid.toHex()}")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("rid", equalTo(block.rid.toHex()))
    }

    @Test
    fun `can get block even when chain is not live`() {
        whenever(
                model.getBlock(BlockRid(block.rid), true)
        ).thenReturn(block)
        whenever(model.live).thenReturn(false)

        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/blocks/$blockchainRID/${block.rid.toHex()}")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("rid", equalTo(block.rid.toHex()))
    }

    @Test
    fun `Block by RID endpoint can return GTV`() {
        whenever(
                model.getBlock(BlockRid(block.rid), true)
        ).thenReturn(block)
        restApi.attachModel(blockchainRID, model)

        val body = given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", ContentType.BINARY)
                .get("/blocks/$blockchainRID/${block.rid.toHex()}")
                .then()
                .statusCode(200)
                .contentType(ContentType.BINARY)

        assertThat(body.extract().response().body.asByteArray())
                .isContentEqualTo(GtvEncoder.encodeGtv(GtvObjectMapper.toGtvDictionary(block)))
    }

    @Test
    fun testGetBlockByUnknownRID() {
        whenever(
                model.getBlock(BlockRid(block.rid), true)
        ).thenReturn(null)
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/blocks/$blockchainRID/${block.rid.toHex()}")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(equalTo("null"))
    }

    @Test
    fun testGetBlockByHeight() {
        whenever(
                model.getBlock(block.height, true)
        ).thenReturn(block)
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/blocks/$blockchainRID/height/${block.height}")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("rid", equalTo(block.rid.toHex()))
    }

    @Test
    fun testGetBlockByUnknownHeight() {
        val height = 0L
        whenever(
                model.getBlock(height, true)
        ).thenReturn(null)
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/blocks/$blockchainRID/height/$height")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(equalTo("null"))
    }

    @Test
    fun testGetBlockByUnknownHeightGTV() {
        val height = 0L
        whenever(
                model.getBlock(height, true)
        ).thenReturn(null)
        restApi.attachModel(blockchainRID, model)

        val body = given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", ContentType.BINARY)
                .get("/blocks/$blockchainRID/height/$height")
                .then()
                .statusCode(200)
                .contentType(ContentType.BINARY)

        assertThat(body.extract().response().body.asByteArray())
                .isContentEqualTo(GtvEncoder.encodeGtv(GtvNull))
    }

    @Test
    fun `Block at height endpoint can return JSON`() {
        whenever(model.getBlock(0, true)).thenReturn(block)

        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
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

        given().basePath(basePath).port(restApi.actualPort())
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

        val body = given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", ContentType.BINARY)
                .get("/blocks/$blockchainRID/height/0")
                .then()
                .statusCode(200)
                .contentType(ContentType.BINARY)

        assertThat(body.extract().response().body.asByteArray())
                .isContentEqualTo(GtvEncoder.encodeGtv(GtvObjectMapper.toGtvDictionary(block)))
    }

    @Test
    fun `Errors are in GTV format when querying for GTV`() {
        given().basePath(basePath).port(restApi.actualPort())
                .header("Accept", ContentType.BINARY)
                .get("/blocks/$blockchainRID/height/0")
                .then()
                .statusCode(404)
                .contentType(ContentType.BINARY)
    }
}
