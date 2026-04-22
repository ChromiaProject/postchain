// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.api.rest.endpoint

import assertk.assertThat
import assertk.assertions.isTrue
import io.restassured.RestAssured
import io.restassured.http.ContentType
import net.postchain.api.rest.controller.HttpExternalModel
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.base.BaseBlockQueries
import net.postchain.base.data.BaseBlockStore
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.concurrent.util.get
import net.postchain.core.Storage
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvInteger
import net.postchain.gtx.GtxQuery
import org.hamcrest.core.IsEqual
import org.http4k.core.Status.Companion.GATEWAY_TIMEOUT
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.lang.Thread.sleep
import java.time.Duration
import java.util.concurrent.CompletionStage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.String

class RestApiTimeoutTest {
    private val basePath = "/api/v1"
    private val blockchainRID = BlockchainRid.ZERO_RID
    private val restApis: MutableList<RestApi> = mutableListOf()

    @AfterEach
    fun tearDown() {
        restApis.forEach { it.close() }
        restApis.clear()
    }

    @Test
    fun `block query timeout - can't be interrupted but returns a response -  API returns timeout error`() {
        val isInterrupted = AtomicBoolean(false)
        val restApi = setupBlockQueriesTest(10) { _, _ ->
            try {
                sleep(1000)
            } catch (_: InterruptedException) {
                isInterrupted.set(true)
            }
            gtv(false)
        }

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .get("/query/$blockchainRID?type=dummy")
                .then()
                .statusCode(INTERNAL_SERVER_ERROR.code)
                .contentType(ContentType.JSON)
                .body(IsEqual("{\"error\":\"Query timed out after 10 ms\"}"))

        assertThat(isInterrupted.get()).isTrue()
    }

    @Test
    fun `block query timeout - request is interrupted and throws an exception - API returns timeout error`() {
        val isInterrupted = AtomicBoolean(false)
        val restApi = setupBlockQueriesTest(10) { _, _ ->
            try {
                sleep(1000)
            } catch (_: InterruptedException) {
                isInterrupted.set(true)
                throw ProgrammerMistake("I got interrupted and can't recover from that")
            }
            gtv(false)
        }

        RestAssured.given().basePath(basePath).port(restApi.actualPort())
                .get("/query/$blockchainRID?type=dummy")
                .then()
                .statusCode(INTERNAL_SERVER_ERROR.code)
                .contentType(ContentType.JSON)
                .body(IsEqual("{\"error\":\"Query timed out after 10 ms\"}"))

        assertThat(isInterrupted.get()).isTrue()
    }

    @Test
    fun `http external model - http request to sub node timeout`() {
        val masterRestApi = setupRestApi()
        val subQueryIsCalled = AtomicBoolean(false)
        val queryStartedLatch = CountDownLatch(1)
        val latch = CountDownLatch(1)
        val subRestApi = setupBlockQueriesTest(Long.MAX_VALUE) { _, _ ->
            queryStartedLatch.countDown()
            subQueryIsCalled.set(true)
            latch.await()
            gtv(false)
        }

        val masterModel = HttpExternalModel(basePath, "http://localhost:${subRestApi.actualPort()}$basePath",
                1L, "", requestTimeoutMs = 1)
        masterRestApi.attachModel(blockchainRID, masterModel)

        try {
            RestAssured.given().basePath(basePath).port(masterRestApi.actualPort())
                    .get("/query/$blockchainRID?type=dummy")
                    .then()
                    .statusCode(GATEWAY_TIMEOUT.code)
            queryStartedLatch.await(5, java.util.concurrent.TimeUnit.SECONDS)
            assertThat(subQueryIsCalled.get()).isTrue()
        } finally {
            // Release the blocked sub-node query so the rest api can shutdown
            latch.countDown()
        }
    }

    @Test
    fun `http external model - sub node query timeout`() {
        val masterRestApi = setupRestApi()
        val isInterrupted = AtomicBoolean(false)
        val subRestApi = setupBlockQueriesTest(10) { _, _ ->
            try {
                sleep(1000)
            } catch (_: InterruptedException) {
                isInterrupted.set(true)
            }
            gtv(false)
        }

        val masterModel = HttpExternalModel(basePath, "http://localhost:${subRestApi.actualPort()}$basePath",
                1L, "")
        masterRestApi.attachModel(blockchainRID, masterModel)

        RestAssured.given().basePath(basePath).port(masterRestApi.actualPort())
                .get("/query/$blockchainRID?type=dummy")
                .then()
                .statusCode(INTERNAL_SERVER_ERROR.code)
                .contentType(ContentType.JSON)
                .body(IsEqual("{\"error\":\"Query timed out after 10 ms\"}"))
        assertThat(isInterrupted.get()).isTrue()
    }

    fun setupRestApi(): RestApi {
        val restApi = RestApi(0, basePath, gracefulShutdown = false)
        restApis.add(restApi)
        return restApi
    }

    fun setupBlockQueriesTest(queryTimeoutMs: Long, queryFunction: (name: String, args: Gtv) -> GtvInteger): RestApi {
        val restApi = setupRestApi()

        val bbq = buildBaseBlockQueriesMock(queryTimeoutMs, queryFunction)
        val model = mock<Model> {
            on { chainIID } doReturn 1L
            on { blockchainRid } doReturn blockchainRID
            on { live } doReturn true
            on { query(any()) } doAnswer {
                val query = it.getArgument<GtxQuery>(0)
                bbq.query(query.name, query.args).get()
            }
        }

        restApi.attachModel(blockchainRID, model)
        return restApi
    }

    private fun buildBaseBlockQueriesMock(queryTimeoutMs: Long, queryFunction: (name: String, args: Gtv) -> GtvInteger): BaseBlockQueries {
        val storage = mock<Storage> {
            on { openReadConnection(anyLong()) } doReturn mock()
        }
        return object : BaseBlockQueries(storage, BaseBlockStore(), 1L, "".toByteArray(),
                mock(), Duration.ofMillis(queryTimeoutMs)) {
            override fun query(name: String, args: Gtv): CompletionStage<Gtv> = runOp {
                queryFunction(name, args)
            }

            override fun queryWithHeight(name: String, args: Gtv): CompletionStage<Pair<Gtv, Long>>
                    = throw NotImplementedError()

            override fun queryWithTimeout(name: String, args: Gtv, queryTimeout: Duration, lockTimeout: Duration): CompletionStage<Gtv>
                    = throw NotImplementedError()

            override fun decodeBlockHeader(headerData: ByteArray) = throw NotImplementedError()

            override fun decodeWitness(witnessData: ByteArray) = throw NotImplementedError()
        }
    }
}
