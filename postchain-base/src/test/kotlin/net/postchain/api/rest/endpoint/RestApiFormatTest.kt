// Copyright (c) 2023 ChromaWay AB. See README for license information.

package net.postchain.api.rest.endpoint

import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.api.rest.json.JsonFactory
import net.postchain.api.rest.model.TxRid
import net.postchain.base.cryptoSystem
import net.postchain.common.BlockchainRid
import net.postchain.common.toHex
import net.postchain.core.BlockRid
import net.postchain.core.TransactionInfoExt
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

class RestApiFormatTest {

    private val basePath = "/api/v1"
    private lateinit var restApi: RestApi
    private lateinit var model: Model
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")
    private val compactGson = JsonFactory.makeJson()

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
    fun `response is compact`() {
        val tx = "tx2".toByteArray()
        val txRID = cryptoSystem.digest(tx)
        val response = TransactionInfoExt(BlockRid.buildRepeat(4).data, 3, "guess what? Another header".toByteArray(),
                "signatures".toByteArray(), 1574849940, txRID, "tx2 - 002".toByteArray().slice(IntRange(0, 4)).toByteArray(), tx)

        whenever(
                model.getTransactionInfo(TxRid(txRID))
        ).thenReturn(response)
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/transactions/$blockchainRID/${txRID.toHex()}")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(equalTo(compactGson.toJson(response)))
    }
}
