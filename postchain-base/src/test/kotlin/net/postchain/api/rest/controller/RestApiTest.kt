// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.controller

import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import net.postchain.common.BlockchainRid
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RestApiTest {

    private val basePath = "/api/v1"
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")
    private lateinit var restApi: RestApi
    private lateinit var model: Model
    private val maxRequestBodySize = 100

    @BeforeEach
    fun setup() {
        model = mock {
            on { chainIID } doReturn 1L
            on { blockchainRid } doReturn blockchainRID
            on { live } doReturn true
        }

        restApi = RestApi(0, basePath, gracefulShutdown = false, clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), maxRequestBodySize = maxRequestBodySize)
    }

    @AfterEach
    fun tearDown() {
        restApi.close()
    }

    @Test
    fun `test request body max limit - acceptable`() {

        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .contentType(ContentType.BINARY)
                .accept(ContentType.BINARY)
                .body(ByteArray(maxRequestBodySize) { 0 }) // Same length as limit
                .post("/tx/$blockchainRID")
                .then()
                .statusCode(200)
        verify(model, times(1)).postTransaction(any())
    }

    @Test
    fun `test request body max limit - too long`() {

        given().basePath(basePath).port(restApi.actualPort())
                .contentType(ContentType.BINARY)
                .accept(ContentType.BINARY)
                .body(ByteArray(maxRequestBodySize + 1) { 0 }) // One byte too much
                .post("/tx/$blockchainRID")
                .then()
                .statusCode(413)
        verify(model, times(0)).postTransaction(any())
    }
}
