package net.postchain.client.request

import org.http4k.core.Request
import org.http4k.core.Response

interface RequestStrategy {
    fun <R> request(createRequest: (Endpoint) -> Request, success: (Response) -> R, failure: (Response) -> R): R
}
