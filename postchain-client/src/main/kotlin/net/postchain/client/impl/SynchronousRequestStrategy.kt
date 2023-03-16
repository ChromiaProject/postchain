package net.postchain.client.impl

import net.postchain.client.request.RequestStrategy
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import java.io.IOException

abstract class SynchronousRequestStrategy(protected val httpClient: HttpHandler) : RequestStrategy {
    protected fun makeRequest(request: Request) = try {
        httpClient(request)
    } catch (e: IOException) {
        Response(Status(Status.SERVICE_UNAVAILABLE.code, e.toString()))
    }
}
