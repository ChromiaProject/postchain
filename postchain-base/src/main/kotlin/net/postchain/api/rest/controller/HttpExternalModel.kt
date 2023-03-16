package net.postchain.api.rest.controller

import kong.unirest.Unirest
import kong.unirest.UnirestException
import mu.KLogging
import spark.Request
import spark.Response

data class HttpExternalModel(
        override val path: String,
        override val chainIID: Long
) : ExternalModel {

    companion object : KLogging()

    override var live = true


    override fun get(request: Request, response: Response): Any {
        return try {
            val url = path + request.uri() + (request.queryString()?.let { "?$it" } ?: "")
            logger.trace { "Redirecting get request to $url" }
            val externalResponse = Unirest.get(url)
                    .header("Accept", request.headers("Accept"))
                    .asBytes()
            response.status(externalResponse.status)
            response.type(externalResponse.headers.get("Content-Type").firstOrNull())
            externalResponse.body
        } catch (e: UnirestException) {
            throw UnavailableException(e.message!!)
        }
    }

    override fun post(request: Request, response: Response): Any {
        return try {
            val url = path + request.uri()
            logger.trace { "Redirecting post request to $url" }
            val externalResponse = Unirest.post(url)
                    .header("Accept", request.headers("Accept"))
                    .header("Content-Type", request.headers("Content-Type"))
                    .body(request.bodyAsBytes())
                    .asBytes()
            response.status(externalResponse.status)
            response.type(externalResponse.headers.get("Content-Type").firstOrNull())
            externalResponse.body
        } catch (e: UnirestException) {
            throw UnavailableException(e.message!!)
        }
    }
}
