package net.postchain.api.rest.controller

import mu.KLogging
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.cookie.StandardCookieSpec
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.util.Timeout
import org.http4k.client.ApacheClient
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Uri

data class HttpExternalModel(
        val basePath: String,
        override val path: String,
        override val chainIID: Long
) : ExternalModel {

    companion object : KLogging()

    override var live = true

    val client: HttpHandler = ApacheClient(HttpClients.custom()
            .setDefaultRequestConfig(RequestConfig.custom()
                    .setRedirectsEnabled(false)
                    .setCookieSpec(StandardCookieSpec.IGNORE)
                    .setConnectionRequestTimeout(Timeout.ofSeconds(60))
                    .setResponseTimeout(Timeout.ofSeconds(60))
                    .build()).build())

    override fun invoke(request: Request): Response {
        val targetUri = Uri.of(path + request.uri.toString().substring(basePath.length))
        logger.trace { "Redirecting ${request.method} request to $targetUri" }
        return client(request.uri(targetUri))
    }
}
