// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.endpoint

import io.restassured.RestAssured.given
import io.restassured.common.mapper.TypeRef
import io.restassured.http.ContentType
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.api.rest.model.ApiRejectedTransaction
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

class RestApiGetRejectedTransactionsEndpointTest {

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
                ApiRejectedTransaction(TxRid(cryptoSystem.digest("tx1".toByteArray())), "one", 1),
                ApiRejectedTransaction(TxRid(cryptoSystem.digest("tx2".toByteArray())), "two", 2),
                ApiRejectedTransaction(TxRid(cryptoSystem.digest("tx3".toByteArray())), "three", 3)
        )
        whenever(
                model.getRejectedTransactions()
        ).thenReturn(response)
        restApi.attachModel(blockchainRID, model)

        val body = given().basePath(basePath).port(restApi.actualPort())
                .get("/tx/$blockchainRID/rejected")
                .`as`(object : TypeRef<MutableList<MutableMap<String?, Any?>?>?>() {})!!

        assertThat(body, hasSize(3))
        assertThat(body[0]!!["txRID"], equalTo("709B55BD3DA0F5A838125BD0EE20C5BFDD7CABA173912D4281CAE816B79A201B"))
        assertThat(body[0]!!["rejectReason"], equalTo("one"))
        assertThat(body[0]!!["rejectTimestamp"], equalTo(1))
        assertThat(body[1]!!["txRID"], equalTo("27CA64C092A959C7EDC525ED45E845B1DE6A7590D173FD2FAD9133C8A779A1E3"))
        assertThat(body[1]!!["rejectReason"], equalTo("two"))
        assertThat(body[1]!!["rejectTimestamp"], equalTo(2))
        assertThat(body[2]!!["txRID"], equalTo("1F3CB18E896256D7D6BB8C11A6EC71F005C75DE05E39BEAE5D93BBD1E2C8B7A9"))
        assertThat(body[2]!!["rejectReason"], equalTo("three"))
        assertThat(body[2]!!["rejectTimestamp"], equalTo(3))
    }

    @Test
    fun empty() {
        val response = listOf<ApiRejectedTransaction>()
        whenever(
                model.getRejectedTransactions()
        ).thenReturn(response)
        restApi.attachModel(blockchainRID, model)

        given().basePath(basePath).port(restApi.actualPort())
                .get("/tx/$blockchainRID/rejected")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .header("Cache-Control", equalTo("private, must-revalidate"))
                .body(equalTo("[]"))
    }
}
