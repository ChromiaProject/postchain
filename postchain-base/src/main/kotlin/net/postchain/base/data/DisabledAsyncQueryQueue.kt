package net.postchain.base.data

import net.postchain.api.rest.controller.NotSupported
import net.postchain.common.types.WrappedByteArray
import net.postchain.core.AsyncQueryQueue
import net.postchain.gtx.GtxQuery

class DisabledAsyncQueryQueue : AsyncQueryQueue {
    override fun enqueueQuery(queryRid: WrappedByteArray, query: GtxQuery) =
            throw NotSupported("Async query is not supported on this blockchain")

    override fun getQueryResponse(queryRid: WrappedByteArray) =
            throw NotSupported("Async query is not supported on this blockchain")

    override fun shutdown() {}
}
