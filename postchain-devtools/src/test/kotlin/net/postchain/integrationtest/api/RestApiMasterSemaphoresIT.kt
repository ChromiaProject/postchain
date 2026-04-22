// Copyright (c) 2020 ChromaWay AB. See README for license information.

package net.postchain.integrationtest.api

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import io.restassured.RestAssured.given
import net.postchain.api.rest.BlockHeight
import net.postchain.api.rest.blockHeightBody
import net.postchain.api.rest.controller.ExternalModel
import net.postchain.api.rest.controller.Model
import net.postchain.api.rest.controller.RestApi
import net.postchain.common.BlockchainRid
import net.postchain.gtv.GtvFactory.gtv
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


class RestApiSemaphoresIT {

    private val blockchainRID1 = BlockchainRid.buildRepeat(0x01)
    private val blockchainRID2 = BlockchainRid.buildRepeat(0x02)
    private val blockchainRID3 = BlockchainRid.buildRepeat(0x03)
    private val blockchainRID4 = BlockchainRid.buildRepeat(0x04)
    private val containerName1 = "container1"
    private val containerName2 = "container2"
    private val containerName3 = "container3"

    @Test
    fun `test container concurrency limit`() {

        val api = RestApi(0, "", requestConcurrency = 5, containerRequestConcurrency = 2)

        val awaitRejectQueryLatch = CountDownLatch(1)
        val awaitOkQueriesSentLatch = CountDownLatch(3)
        api.attachModel(blockchainRID1, mockExternalModel(containerName1) {
            awaitOkQueriesSentLatch.countDown()
            awaitRejectQueryLatch.await()
        }, containerName1)
        api.attachModel(blockchainRID2, mockExternalModel(containerName2) {
            awaitOkQueriesSentLatch.countDown()
            awaitRejectQueryLatch.await()
        }, containerName1)

        val okQueries = runAsyncOkQueries(api.server.port(), listOf(
                "/query/$blockchainRID1?type=200-1", // Consumes first lock
                "/query/$blockchainRID1?type=200-2", // Consumes second lock
                "/query/$blockchainRID2?type=200-1", // Consumes first lock, but on different chain
        ))

        awaitOkQueriesSentLatch.await()
        val rejectedResponse = given().port(api.server.port())
                .get("/query/$blockchainRID1?type=503")
                .then()
                .extract()
        assertThat(rejectedResponse.statusCode()).isEqualTo(503)
        assertThat(rejectedResponse.body().asString()).contains("Too many concurrent requests for container container1")

        awaitRejectQueryLatch.countDown()
        CompletableFuture.allOf(*okQueries).get(5, TimeUnit.SECONDS)
        okQueries.forEach { assertThat(it.get()).isEqualTo(200) }

        // Make sure it is released
        assertThat(runAsyncOkQueries(api.server.port(), listOf(
                "/query/$blockchainRID1?type=200-1",
        ))[0].get()).isEqualTo(200)
    }

    @Test
    fun `test external model concurrency limit`() {

        val api = RestApi(0, "", requestConcurrency = 4, requestConcurrencyExternal = 2)

        val awaitRejectQueryLatch = CountDownLatch(1)
        val awaitOkQueriesSentLatch = CountDownLatch(2)
        api.attachModel(blockchainRID1, mockExternalModel(containerName1) {
            awaitOkQueriesSentLatch.countDown()
            awaitRejectQueryLatch.await()
        }, containerName1)

        val okQueries = runAsyncOkQueries(api.server.port(), listOf(
                "/query/$blockchainRID1?type=200-1",
                "/query/$blockchainRID1?type=200-2",
        ))

        awaitOkQueriesSentLatch.await()
        val rejectedResponse = given().port(api.server.port())
                .get("/query/$blockchainRID1?type=503")
                .then()
                .extract()
        assertThat(rejectedResponse.statusCode()).isEqualTo(503)
        assertThat(rejectedResponse.body().asString()).contains("Too many concurrent requests for subnode containers")

        awaitRejectQueryLatch.countDown()
        CompletableFuture.allOf(*okQueries).get(5, TimeUnit.SECONDS)
        okQueries.forEach { assertThat(it.get()).isEqualTo(200) }

        // Make sure it is released
        assertThat(runAsyncOkQueries(api.server.port(), listOf(
                "/query/$blockchainRID1?type=200-1",
        ))[0].get()).isEqualTo(200)
    }

    @Test
    fun `test combination of chain, external model and container limits`() {

        val okQueryPaths = listOf(
                "/query/$blockchainRID1?type=chain-full-1", // Consume locks: container 1, brid 1, external model
                "/query/$blockchainRID1?type=chain-full-2", // Consume locks: container 1, brid 1, external model
                "/query/$blockchainRID2?type=container-full-1", // Consume locks: container 2, brid 2, external model
                "/query/$blockchainRID2?type=container-full-2", // Consume locks: container 2, brid 2, external model
                "/query/$blockchainRID3?type=container-full-3", // Consume locks: container 2, brid 3, external model
        )
        val api = RestApi(0, "",
                requestConcurrency = okQueryPaths.size + 3,
                requestConcurrencyExternal = okQueryPaths.size + 1,
                containerRequestConcurrency = 3,
                chainRequestConcurrency = 2
        )
        val awaitRejectQueryLatch = CountDownLatch(1)
        val awaitOkQueriesSentLatch = CountDownLatch(okQueryPaths.size)
        val awaitFillUpExternalModelSemaphoreLatch = CountDownLatch(1)
        api.attachModel(blockchainRID1, mockExternalModel(containerName1) {
            awaitOkQueriesSentLatch.countDown()
            awaitRejectQueryLatch.await()
        }, containerName1)
        api.attachModel(blockchainRID2, mockExternalModel(containerName2) {
            awaitOkQueriesSentLatch.countDown()
            awaitRejectQueryLatch.await()
        }, containerName2)
        api.attachModel(blockchainRID3, mockExternalModel(containerName2) {
            awaitOkQueriesSentLatch.countDown()
            awaitRejectQueryLatch.await()
        }, containerName2)

        val okQueries = runAsyncOkQueries(api.server.port(), okQueryPaths)
        awaitOkQueriesSentLatch.await()

        // A new request to brid 1 should be rejected
        val rejectedChainResponse = given().port(api.server.port())
                .get("/query/$blockchainRID1?type=503")
                .then()
                .extract()
        assertThat(rejectedChainResponse.statusCode()).isEqualTo(503)
        assertThat(rejectedChainResponse.body().asString()).contains(
                "Too many concurrent requests for blockchain 0101010101010101010101010101010101010101010101010101010101010101")

        // A new request to brid 3 should be rejected
        val rejectedContainerResponse = given().port(api.server.port())
                .get("/query/$blockchainRID3?type=503")
                .then()
                .extract()
        assertThat(rejectedContainerResponse.statusCode()).isEqualTo(503)
        assertThat(rejectedContainerResponse.body().asString()).contains(
                "Too many concurrent requests for container container2")

        // Fill up the external model semaphore
        api.attachModel(blockchainRID4, mockExternalModel(containerName3) {
            awaitFillUpExternalModelSemaphoreLatch.countDown()
            awaitRejectQueryLatch.await()
        }, containerName3)
        val fillUpExternalModelRequest = runAsyncOkQueries(api.server.port(), listOf(
                "/query/$blockchainRID4?type=external-model-full-1", // Consume locks: container 4, brid 4, external model
        ))
        awaitFillUpExternalModelSemaphoreLatch.await()

        // A new request to any brid should be rejected
        val rejectedExternalModelResponse = given().port(api.server.port())
                .get("/query/$blockchainRID4?type=503")
                .then()
                .extract()
        assertThat(rejectedExternalModelResponse.statusCode()).isEqualTo(503)
        assertThat(rejectedExternalModelResponse.body().asString()).contains(
                "Too many concurrent requests for subnode containers")

        awaitRejectQueryLatch.countDown()
        assertThat(fillUpExternalModelRequest[0].get()).isEqualTo(200)
        CompletableFuture.allOf(*okQueries).get(5, TimeUnit.SECONDS)
        okQueries.forEach { assertThat(it.get()).isEqualTo(200) }

        // Make sure it is released
        assertThat(runAsyncOkQueries(api.server.port(), listOf(
                "/query/$blockchainRID1?type=200-1",
        ))[0].get()).isEqualTo(200)
    }

    @Test
    fun `test internal model concurrency limit`() {

        val api = RestApi(0, "", requestConcurrency = 4, requestConcurrencyLocal = 2)

        val awaitRejectQueryLatch = CountDownLatch(1)
        val awaitOkQueriesSentLatch = CountDownLatch(2)
        api.attachModel(blockchainRID1, mockLocalModel {
            awaitOkQueriesSentLatch.countDown()
            awaitRejectQueryLatch.await()
        }, containerName1)

        val okQueries = runAsyncOkQueries(api.server.port(), listOf(
                "/query/$blockchainRID1?type=200-1",
                "/query/$blockchainRID1?type=200-2",
        ))

        awaitOkQueriesSentLatch.await()
        val rejectedResponse = given().port(api.server.port())
                .get("/query/$blockchainRID1?type=503")
                .then()
                .extract()
        assertThat(rejectedResponse.statusCode()).isEqualTo(503)
        assertThat(rejectedResponse.body().asString()).contains("Too many concurrent requests for internal models")

        awaitRejectQueryLatch.countDown()
        CompletableFuture.allOf(*okQueries).get(5, TimeUnit.SECONDS)
        okQueries.forEach { assertThat(it.get()).isEqualTo(200) }

        // Make sure it is released
        assertThat(runAsyncOkQueries(api.server.port(), listOf(
                "/query/$blockchainRID1?type=200-1",
        ))[0].get()).isEqualTo(200)
    }

    private fun mockExternalModel(containerName: String, invoke: () -> Unit): ExternalModel {
        return mock {
            on { path } doReturn "http://localhost:1"
            on { directoryContainer } doReturn containerName
            on { chainIID } doReturn 1L
            on { live } doReturn true
            on { invoke(any()) } doAnswer {
                invoke()
                Response(Status.OK).with(blockHeightBody of BlockHeight(17))
            }
        }
    }

    private fun mockLocalModel(invoke: () -> Unit): Model {
        return mock {
            on { chainIID } doReturn 1L
            on { live } doReturn true
            on { query(any()) } doAnswer {
                invoke()
                gtv(true)
            }
        }
    }

    private fun runAsyncOkQueries(port: Int, queries: List<String>): Array<CompletableFuture<Int>> {
        return queries.map {
            CompletableFuture.supplyAsync {
                given().port(port)
                        .get(it)
                        .then()
                        .extract().statusCode()
            }
        }.toTypedArray()
    }
}
