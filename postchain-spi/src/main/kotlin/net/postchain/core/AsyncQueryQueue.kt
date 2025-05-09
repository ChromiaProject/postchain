package net.postchain.core

import net.postchain.common.types.WrappedByteArray
import net.postchain.gtx.GtxQuery

/**
 * A queue for asynchronous query processing.
 */
interface AsyncQueryQueue : Shutdownable {

    /**
     * Adds a query to the queue.
     *
     * @param queryRid The RID of the query
     * @param query The query to execute
     */
    fun enqueueQuery(queryRid: WrappedByteArray, query: GtxQuery)

    /**
     * Gets the response for a query.
     *
     * @param queryRid The RID of the query
     * @return The query response, or null if the query ID is not found
     */
    fun getQueryResponse(queryRid: WrappedByteArray): AsyncQueryResponse
}