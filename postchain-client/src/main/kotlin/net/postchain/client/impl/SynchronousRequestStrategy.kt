package net.postchain.client.impl

import net.postchain.client.request.RequestStrategy
import org.http4k.core.HttpHandler
import org.http4k.core.Request

abstract class SynchronousRequestStrategy(protected val httpClient: HttpHandler) : RequestStrategy {
    protected fun makeRequest(request: Request) = httpClient(request)
}
