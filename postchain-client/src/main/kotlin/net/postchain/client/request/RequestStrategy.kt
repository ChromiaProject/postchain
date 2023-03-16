package net.postchain.client.request

import org.http4k.core.Request
import org.http4k.core.Response
import java.io.Closeable

interface RequestStrategy : Closeable {
    fun <R> request(createRequest: (Endpoint) -> Request, success: (Response) -> R, failure: (Response) -> R, queryMultiple: Boolean): R
}
