// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.endpoint

import io.restassured.RestAssured.given
import io.restassured.common.mapper.TypeRef
import io.restassured.http.ContentType
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.api.rest.model.TxRid
import net.postchain.common.BlockchainRid
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RestApiGetWaitingTransactionsEndpointTest {

    private val basePath = "/api/v1"
    private lateinit var restApi: RestApi
    private lateinit var model: Model
    private val blockchainRID = BlockchainRid.buildFromHex("78967baa4768cbcef11c508326ffb13a956689fcb6dc3ba17f4b895cbb1577a3")

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
    fun three() {
        val response = listOf(
                TxRid(cryptoSystem.digest("tx1".toByteArray())),
                TxRid(cryptoSystem.digest("tx2".toByteArray())),
                TxRid(cryptoSystem.digest("tx3".toByteArray())),
        )
        whenever(
                model.getWaitingTransactions()
        ).thenReturn(response)
        restApi.attachModel(blockchainRID, model)

        val body = given().basePath(basePath).port(restApi.actualPort())
                .get("/tx/$blockchainRID/waiting")
                .`as`(object : TypeRef<MutableList<Any?>?>() {})!!

        assertThat(body, hasSize(3))
        assertThat(body[0], equalTo("709B55BD3DA0F5A838125BD0EE20C5BFDD7CABA173912D4281CAE816B79A201B"))
        assertThat(body[1], equalTo("27CA64C092A959C7EDC525ED45E845B1DE6A7590D173FD2FAD9133C8A779A1E3"))
        assertThat(body[2], equalTo("1F3CB18E896256D7D6BB8C11A6EC71F005C75DE05E39BEAE5D93BBD1E2C8B7A9"))
    }

    @Test
    fun empty() {
        val response = listOf<TxRid>()
        whenever(
                model.getWaitingTransactions()
        ).thenReturn(response)
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/tx/$blockchainRID/waiting")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .header("Cache-Control", equalTo("private, must-revalidate"))
                .body(equalTo("[]"))
    }
}
