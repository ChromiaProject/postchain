package net.postchain.base.data

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import net.postchain.api.rest.controller.DuplicateException
import net.postchain.api.rest.controller.UnavailableException
import net.postchain.common.exception.UserMistake
import net.postchain.common.wrap
import net.postchain.core.AsyncQueryResponseStatus
import net.postchain.core.EContext
import net.postchain.crypto.sha256Digest
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorBase
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.gtv.merkleHash
import net.postchain.gtx.GtxQuery
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class BaseAsyncQueryQueueTest {
    private val hashCalculator: GtvMerkleHashCalculatorBase = GtvMerkleHashCalculatorV2(::sha256Digest)
    private lateinit var asyncQueryQueue: BaseAsyncQueryQueue

    @AfterEach
    fun tearDown() {
        if (::asyncQueryQueue.isInitialized) {
            asyncQueryQueue.shutdown()
        }
    }

    @Test
    fun `enqueue and process query successfully`() {
        // Arrange
        val queryExecutor: (EContext, GtxQuery) -> Gtv = mock {
            on { invoke(any(), any()) } doReturn gtv("success")
        }
        val mockStorage: BaseStorage = mock {
            on { openReadConnection(any()) } doReturn mock {}
        }
        asyncQueryQueue = BaseAsyncQueryQueue(
                queueCapacity = 10,
                queryTimeoutSeconds = 10,
                resultRetentionSeconds = 10,
                storage = mockStorage,
                chainID = 1,
                queryExecutor = queryExecutor,
        )

        // Act
        val query = GtxQuery("my_query", gtv(mapOf()))
        val queryRid = query.toGtv().merkleHash(hashCalculator).wrap()
        asyncQueryQueue.enqueueQuery(queryRid, query)

        // Wait for the query to be processed
        Thread.sleep(500)

        // Assert
        val response = asyncQueryQueue.getQueryResponse(queryRid)
        assertThat(response.status).isEqualTo(AsyncQueryResponseStatus.COMPLETED)
        assertThat(response.queryResponse).isEqualTo(gtv("success"))
        assertThat(response.errorMessage).isEqualTo(null)
        verify(queryExecutor, times(1)).invoke(any(), eq(query))
    }

    @Test
    fun `handle query execution error`() {
        // Arrange
        val queryExecutor: (EContext, GtxQuery) -> Gtv = mock {
            on { invoke(any(), any()) } doThrow UserMistake("Test error")
        }
        val mockStorage: BaseStorage = mock {
            on { openReadConnection(any()) } doReturn mock {}
        }
        asyncQueryQueue = BaseAsyncQueryQueue(
                queueCapacity = 10,
                queryTimeoutSeconds = 10,
                resultRetentionSeconds = 10,
                storage = mockStorage,
                chainID = 1,
                queryExecutor = queryExecutor,
        )

        // Act
        val query = GtxQuery("my_query", gtv(mapOf()))
        val queryRid = query.toGtv().merkleHash(hashCalculator).wrap()
        asyncQueryQueue.enqueueQuery(queryRid, query)

        // Wait for the query to be processed
        Thread.sleep(500)

        // Assert
        val response = asyncQueryQueue.getQueryResponse(queryRid)
        assertThat(response.status).isEqualTo(AsyncQueryResponseStatus.FAILED)
        assertThat(response.queryResponse).isEqualTo(GtvNull)
        assertThat(response.errorMessage).isEqualTo("Test error")
        verify(queryExecutor, times(1)).invoke(any(), eq(query))
    }

    @Test
    fun `respect queue capacity limit`() {
        // Arrange
        val latch = CountDownLatch(1)
        val queryExecutor: (EContext, GtxQuery) -> Gtv = mock {
            on { invoke(any(), any()) } doAnswer {
                // Block the executor thread to fill up the queue
                latch.await(5, TimeUnit.SECONDS)
                gtv("success")
            }
        }
        val mockStorage: BaseStorage = mock {
            on { openReadConnection(any()) } doReturn mock {}
        }
        asyncQueryQueue = BaseAsyncQueryQueue(
                queueCapacity = 1, // Small capacity to test limits
                queryTimeoutSeconds = 10,
                resultRetentionSeconds = 10,
                storage = mockStorage,
                chainID = 1,
                queryExecutor = queryExecutor,
        )

        // Act
        val query1 = GtxQuery("my_query1", gtv(mapOf()))
        val queryRid1 = query1.toGtv().merkleHash(hashCalculator).wrap()
        val query2 = GtxQuery("my_query2", gtv(mapOf()))
        val queryRid2 = query2.toGtv().merkleHash(hashCalculator).wrap()
        val query3 = GtxQuery("my_query3", gtv(mapOf()))
        val queryRid3 = query3.toGtv().merkleHash(hashCalculator).wrap()

        asyncQueryQueue.enqueueQuery(queryRid1, query1)
        asyncQueryQueue.enqueueQuery(queryRid2, query2)

        // Wait a bit to ensure the first query is being processed
        Thread.sleep(200)

        assertFailure {
            asyncQueryQueue.enqueueQuery(queryRid3, query3)
        }.isInstanceOf(UnavailableException::class)

        // Release the latch to allow the executor to continue
        latch.countDown()

        // Wait for the query to be processed
        Thread.sleep(500)

        // Assert
        val response1 = asyncQueryQueue.getQueryResponse(queryRid1)
        assertThat(response1.status).isEqualTo(AsyncQueryResponseStatus.COMPLETED)
        assertThat(response1.queryResponse).isEqualTo(gtv("success"))
        assertThat(response1.errorMessage).isEqualTo(null)
        verify(queryExecutor, times(1)).invoke(any(), eq(query1))
        val response2 = asyncQueryQueue.getQueryResponse(queryRid2)
        assertThat(response2.status).isEqualTo(AsyncQueryResponseStatus.COMPLETED)
        assertThat(response2.queryResponse).isEqualTo(gtv("success"))
        assertThat(response2.errorMessage).isEqualTo(null)
        verify(queryExecutor, times(1)).invoke(any(), eq(query2))
    }

    @Test
    fun `return not found for unknown query RID`() {
        // Arrange
        val mockStorage: BaseStorage = mock {
            on { openReadConnection(any()) } doReturn mock {}
        }
        asyncQueryQueue = BaseAsyncQueryQueue(
                queueCapacity = 10,
                queryTimeoutSeconds = 10,
                resultRetentionSeconds = 10,
                storage = mockStorage,
                chainID = 1,
        ) { ctx, query ->
            gtv("success")
        }

        // Act
        val response = asyncQueryQueue.getQueryResponse(ByteArray(32) { it.toByte() }.wrap())

        // Assert
        assertThat(response.status).isEqualTo(AsyncQueryResponseStatus.NOT_FOUND)
        assertThat(response.queryResponse).isEqualTo(GtvNull)
        assertThat(response.errorMessage).isEqualTo(null)
    }

    @Test
    fun `handle query timeout`() {
        val interrupted = AtomicBoolean(false)

        // Arrange
        val mockStorage: BaseStorage = mock {
            on { openReadConnection(any()) } doReturn mock {}
        }
        val queryExecutor: (EContext, GtxQuery) -> Gtv = mock {
            on { invoke(any(), any()) } doAnswer {
                try {
                    Thread.sleep(2000) // Sleep longer than timeout
                } catch (e: InterruptedException) {
                    interrupted.set(true)
                    throw e
                }
                gtv("success")
            }
        }
        asyncQueryQueue = BaseAsyncQueryQueue(
                queueCapacity = 10,
                queryTimeoutSeconds = 1, // Short timeout
                resultRetentionSeconds = 10,
                storage = mockStorage,
                chainID = 1,
                queryExecutor = queryExecutor,
        )

        // Act
        val query = GtxQuery("my_query", gtv(mapOf()))
        val queryRid = query.toGtv().merkleHash(hashCalculator).wrap()
        asyncQueryQueue.enqueueQuery(queryRid, query)

        // Wait for the query to timeout
        Thread.sleep(1500)

        // Assert
        val response = asyncQueryQueue.getQueryResponse(queryRid)
        assertThat(response.status).isEqualTo(AsyncQueryResponseStatus.FAILED)
        assertThat(response.queryResponse).isEqualTo(GtvNull)
        assertThat(response.errorMessage).isEqualTo("Query timed out after 1 seconds")
        verify(queryExecutor, times(1)).invoke(any(), eq(query))
        assertThat(interrupted.get()).isTrue()
    }

    @Test
    fun `test duplicate query`() {
        // Arrange
        val queryExecutor: (EContext, GtxQuery) -> Gtv = mock {
            on { invoke(any(), any()) } doReturn gtv("success")
        }
        val mockStorage: BaseStorage = mock {
            on { openReadConnection(any()) } doReturn mock {}
        }
        asyncQueryQueue = BaseAsyncQueryQueue(
                queueCapacity = 10,
                queryTimeoutSeconds = 10,
                resultRetentionSeconds = 10,
                storage = mockStorage,
                chainID = 1,
                queryExecutor = queryExecutor
        )

        // Act
        val query = GtxQuery("my_query", gtv(mapOf()))
        val queryRid = query.toGtv().merkleHash(hashCalculator).wrap()
        asyncQueryQueue.enqueueQuery(queryRid, query)

        // Assert
        assertFailure {
            asyncQueryQueue.enqueueQuery(queryRid, query)
        }.isInstanceOf(DuplicateException::class)
    }

    @Test
    fun `test result retention period`() {
        // Arrange
        val queryExecutor: (EContext, GtxQuery) -> Gtv = mock {
            on { invoke(any(), any()) } doReturn gtv("success")
        }
        val mockStorage: BaseStorage = mock {
            on { openReadConnection(any()) } doReturn mock {}
        }
        asyncQueryQueue = BaseAsyncQueryQueue(
                queueCapacity = 10,
                queryTimeoutSeconds = 10,
                resultRetentionSeconds = 1, // Short retention period
                storage = mockStorage,
                chainID = 1,
                queryExecutor = queryExecutor,
        )

        // Act
        val query = GtxQuery("my_query", gtv(mapOf()))
        val queryRid = query.toGtv().merkleHash(hashCalculator).wrap()
        asyncQueryQueue.enqueueQuery(queryRid, query)

        // Wait for the query to be processed
        Thread.sleep(500)

        // Assert query result is available
        val response1 = asyncQueryQueue.getQueryResponse(queryRid)
        assertThat(response1.status).isEqualTo(AsyncQueryResponseStatus.COMPLETED)

        // Wait for the retention period to expire
        Thread.sleep(1500)

        // Assert query result is removed
        val response2 = asyncQueryQueue.getQueryResponse(queryRid)
        assertThat(response2.status).isEqualTo(AsyncQueryResponseStatus.NOT_FOUND)
    }
}
