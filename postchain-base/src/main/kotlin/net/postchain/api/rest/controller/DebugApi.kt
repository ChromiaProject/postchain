// Copyright (c) 2023 ChromaWay AB. See README for license information.

package net.postchain.api.rest.controller

import com.google.common.util.concurrent.ThreadFactoryBuilder
import io.netty.channel.nio.NioEventLoopGroup
import mu.KLogging
import net.postchain.api.rest.ErrorBody
import net.postchain.api.rest.controller.http4k.NettyWithCustomWorkerGroup
import net.postchain.api.rest.errorBody
import net.postchain.api.rest.prettyJsonBody
import net.postchain.api.rest.queryQuery
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.filter.ServerFilters
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.server.ServerConfig
import org.http4k.server.asServer
import java.io.Closeable
import java.time.Duration

/**
 * Implements the debug API.
 *
 * @param requestConcurrency  number of incoming HTTP requests to handle concurrently,
 *                            specify 0 for default value based on number of available processor cores
 */
class DebugApi(
        private val listenPort: Int,
        private val debugInfoQuery: DebugInfoQuery,
        gracefulShutdown: Boolean = true,
        requestConcurrency: Int = 0
) : Closeable {

    companion object : KLogging()

    fun actualPort(): Int = server.port()

    private val app = routes(
            "/" bind static(ResourceLoader.Classpath("/debugapi-root")),
            "/_debug" bind GET to ::getDebug
    )

    private fun getDebug(request: Request): Response {
        val debugInfo = debugInfoQuery.queryDebugInfo(queryQuery(request))
        return Response(OK).with(prettyJsonBody of debugInfo)
    }

    val server = ServerFilters.GZip()
            .then(ServerFilters.CatchLensFailure { request, lensFailure ->
                logger.info { "Bad request: ${lensFailure.message}" }
                Response(BAD_REQUEST).with(
                        errorBody.outbound(request) of ErrorBody(lensFailure.failures.joinToString("; "))
                )
            })
            .then(app)
            .asServer(NettyWithCustomWorkerGroup(
                    listenPort,
                    if (gracefulShutdown) ServerConfig.StopMode.Graceful(Duration.ofSeconds(5)) else ServerConfig.StopMode.Immediate,
                    NioEventLoopGroup(requestConcurrency, ThreadFactoryBuilder().setNameFormat("DEBUG-API-%d").build()))
            )
            .start().also {
                logger.info { "Debug API listening on port ${it.port()} and were given $listenPort, attached on /" }
            }

    override fun close() {
        server.close()
        System.gc()
        System.runFinalization()
    }
}
