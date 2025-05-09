package net.postchain.base.data

import com.google.common.util.concurrent.ThreadFactoryBuilder
import mu.KLogging
import net.postchain.api.rest.controller.DuplicateException
import net.postchain.api.rest.controller.UnavailableException
import net.postchain.common.exception.UserMistake
import net.postchain.common.types.WrappedByteArray
import net.postchain.core.AsyncQueryQueue
import net.postchain.core.AsyncQueryResponse
import net.postchain.core.AsyncQueryResponseStatus
import net.postchain.core.EContext
import net.postchain.core.Storage
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtx.GtxQuery
import org.postgresql.PGConnection
import java.sql.Connection
import java.sql.SQLException
import java.sql.SQLTimeoutException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class BaseAsyncQueryQueue(
        queueCapacity: Int,
        private val queryTimeoutSeconds: Long,
        private val resultRetentionSeconds: Long,
        private val storage: Storage,
        private val chainID: Long,
        private val queryExecutor: (EContext, GtxQuery) -> Gtv,
) : AsyncQueryQueue {

    companion object : KLogging()

    private val results = ConcurrentHashMap<WrappedByteArray, AsyncQueryResponse>() // rid -> response

    private val executor: ExecutorService = ThreadPoolExecutor(
            1,
            1,
            0L, TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(queueCapacity),
            ThreadFactoryBuilder().setNameFormat("AsyncQuery-executor-%d").build(),
            ThreadPoolExecutor.AbortPolicy(),
    )

    private val timeouter: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
            ThreadFactoryBuilder().setNameFormat("AsyncQuery-timeout-%d").setDaemon(true).build()
    )

    /**
     * Adds a query to the queue.
     *
     * @param queryRid The RID of the query
     * @param query The query to execute
     * @throws UnavailableException if the queue is full
     * @throws DuplicateException if the query RID is already in the queue
     */
    override fun enqueueQuery(queryRid: WrappedByteArray, query: GtxQuery) {
        if (results.putIfAbsent(queryRid, AsyncQueryResponse(
                        status = AsyncQueryResponseStatus.PENDING,
                        queryResponse = GtvNull,
                        errorMessage = null
                )) != null) {
            logger.debug { "Query $queryRid already in queue" }
            throw DuplicateException("Query already in queue")
        }

        try {
            val timeoutFuture = AtomicReference<ScheduledFuture<*>?>()
            val connection = AtomicReference<Connection?>()
            val future = executor.submit {
                try {
                    logger.debug { "Processing query $queryRid" }
                    val ctx = storage.openReadConnection(chainID)
                    val result = try {
                        connection.set(ctx.conn)
                        queryExecutor(ctx, query)
                    } finally {
                        connection.set(null)
                        storage.closeReadConnection(ctx)
                    }
                    results.replace(queryRid, AsyncQueryResponse(
                            status = AsyncQueryResponseStatus.COMPLETED,
                            queryResponse = result,
                            errorMessage = null
                    ))
                    logger.debug { "Query $queryRid completed successfully" }
                } catch (e: InterruptedException) {
                    logger.debug(e) { "Query $queryRid got InterruptedException" }
                    results.replace(queryRid, AsyncQueryResponse(
                            status = AsyncQueryResponseStatus.FAILED,
                            queryResponse = GtvNull,
                            errorMessage = "Query timed out after $queryTimeoutSeconds seconds"))
                } catch (e: SQLTimeoutException) {
                    logger.debug(e) { "Query $queryRid got SQLTimeoutException" }
                    results.replace(queryRid, AsyncQueryResponse(
                            status = AsyncQueryResponseStatus.FAILED,
                            queryResponse = GtvNull,
                            errorMessage = "Query timed out after $queryTimeoutSeconds seconds"))
                } catch (e: SQLException) {
                    if (e.sqlState == "57014") { // query_canceled https://www.postgresql.org/docs/16/errcodes-appendix.html
                        logger.debug(e) { "Query $queryRid got SQLException with SQL State 57014 query_canceled" }
                        results.replace(queryRid, AsyncQueryResponse(
                                status = AsyncQueryResponseStatus.FAILED,
                                queryResponse = GtvNull,
                                errorMessage = "Query timed out after $queryTimeoutSeconds seconds"))
                    } else {
                        logger.warn(e) { "Unexpected error processing query $queryRid: $e" }
                        results.replace(queryRid, AsyncQueryResponse(
                                status = AsyncQueryResponseStatus.FAILED,
                                queryResponse = GtvNull,
                                errorMessage = "Unknown error"
                        ))
                    }
                } catch (e: UserMistake) {
                    logger.debug { "Error processing query $queryRid: ${e.message}" }
                    results.replace(queryRid, AsyncQueryResponse(
                            status = AsyncQueryResponseStatus.FAILED,
                            queryResponse = GtvNull,
                            errorMessage = e.message ?: "Unknown error"
                    ))
                } catch (e: Exception) {
                    logger.warn(e) { "Unexpected error processing query $queryRid: $e" }
                    results.replace(queryRid, AsyncQueryResponse(
                            status = AsyncQueryResponseStatus.FAILED,
                            queryResponse = GtvNull,
                            errorMessage = "Unknown error"
                    ))
                } finally {
                    timeoutFuture.get()?.cancel(false)
                    timeouter.schedule({
                        logger.debug { "Clean-up $queryRid" }
                        results.remove(queryRid)
                    }, resultRetentionSeconds, TimeUnit.SECONDS)
                }
            }
            if (!future.isDone) {
                timeoutFuture.set(timeouter.schedule({
                    logger.warn("Query $queryRid timed out after $queryTimeoutSeconds seconds, attempting to cancel")
                    future.cancel(true) // interrupt the query thread
                    connection.get()?.let {
                        if (it.isWrapperFor(PGConnection::class.java)) {
                            val postgresConnection = it.unwrap(PGConnection::class.java)
                            try {
                                postgresConnection.cancelQuery()
                            } catch (e: SQLException) {
                                logger.warn { "Failed to cancel query $queryRid: $e" }
                            }
                        }
                    }
                    timeouter.schedule({
                        logger.debug { "Clean-up $queryRid" }
                        results.remove(queryRid)
                    }, resultRetentionSeconds, TimeUnit.SECONDS)
                }, queryTimeoutSeconds, TimeUnit.SECONDS))
            }
            logger.debug { "Query $queryRid enqueued" }
        } catch (_: RejectedExecutionException) {
            logger.debug { "Query queue is full" }
            results.remove(queryRid)
            throw UnavailableException("Query queue is full")
        }
    }

    /**
     * Gets the response for a query.
     *
     * @param queryRid The RID of the query
     * @return The query response, or null if the query ID is not found
     */
    override fun getQueryResponse(queryRid: WrappedByteArray): AsyncQueryResponse {
        return results[queryRid] ?: AsyncQueryResponse(
                status = AsyncQueryResponseStatus.NOT_FOUND,
                queryResponse = GtvNull,
                errorMessage = null
        )
    }

    /**
     * Closes the query queue and stops the executor.
     */
    override fun shutdown() {
        timeouter.shutdownNow()
        executor.shutdownNow()
        if (!timeouter.awaitTermination(1, TimeUnit.SECONDS)) {
            logger.warn("Timeouter did not terminate in time")
        }
        if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
            logger.warn("Executor did not terminate in time")
        }
    }
}
