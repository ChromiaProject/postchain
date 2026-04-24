// Copyright (c) 2023 ChromaWay AB. See README for license information.

package net.postchain.api.rest

import io.restassured.RestAssured
import io.restassured.http.ContentType
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.api.rest.endpoint.cryptoSystem
import net.postchain.api.rest.json.JsonFactory
import net.postchain.api.rest.model.TxRid
import net.postchain.base.BaseBlockWitness
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.core.BlockRid
import net.postchain.core.TransactionInfoExt
import net.postchain.crypto.Signature
import org.hamcrest.CoreMatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RestApiFormatTest {

    private val basePath = "/api/v1"
    private lateinit var restApi: RestApi
    private lateinit var model: Model
    private val compactGson = JsonFactory.makeJson()
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")
    private val witness1 = "0320F0B9E7ECF1A1568C31644B04D37ADC05327F996B9F48220E301DC2FEE6F8FF".hexStringToByteArray()
    private val witness2 = "0307C88BF37C528B14AF95E421749E72F6DA88790BCE74890BDF780D854D063C40".hexStringToByteArray()
    private val signature1 = ByteArray(16) { 1 }
    private val signature2 = ByteArray(16) { 2 }
    private val witness = BaseBlockWitness.fromSignatures(arrayOf(
            Signature(witness1, signature1),
            Signature(witness2, signature2)
    ))

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
    fun `response is compact`() {
        val tx = "tx2".toByteArray()
        val txRID = cryptoSystem.digest(tx)
        val response = TransactionInfoExt(BlockRid.buildRepeat(4).data, 3, "guess what? Another header".toByteArray(),
                witness.getRawData(), 1574849940, txRID, "tx2 - 002".toByteArray().slice(IntRange(0, 4)).toByteArray(), tx)

        whenever(
                model.getTransactionInfo(TxRid(txRID), true)
        ).thenReturn(response)
        restApi.attachModel(blockchainRID, model)

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .get("/transactions/$blockchainRID/${txRID.toHex()}")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(CoreMatchers.equalTo(compactGson.toJson(response)))
    }
}