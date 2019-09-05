package net.postchain.api.rest.controller

import mu.KLogging
import net.postchain.api.rest.json.JsonFactory
import net.postchain.core.UserMistake
import spark.Service

class HttpServer {

    val http = Service.ignite()!!
    private val gson = JsonFactory.makeJson()
    companion object : KLogging()

    init {
        buildErrorHandler(http)
    }

    private fun buildErrorHandler(http: Service) {
        http.exception(NotFoundError::class.java) { error, _, response ->
            logger.error("NotFoundError:", error)
            response.status(404)
            response.body(error(error))
        }

        http.exception(BadFormatError::class.java) { error, _, response ->
            logger.error("BadFormatError:", error)
            response.status(400)
            response.body(error(error))
        }

        http.exception(UserMistake::class.java) { error, _, response ->
            logger.error("UserMistake:", error)
            response.status(400)
            response.body(error(error))
        }

        http.exception(OverloadedException::class.java) { error, _, response ->
            response.status(503) // Service unavailable
            response.body(error(error))
        }

        http.exception(Exception::class.java) { error, _, response ->
            logger.error("Exception:", error)
            response.status(500)
            response.body(error(error))
        }

        http.notFound { _, _ -> error(UserMistake("Not found")) }
    }

    fun initialize(listenPort: Int, sslCertificate: String? = null, sslCertificatePassword: String? = null) {
        http.port(listenPort)
        if (sslCertificate != null) {
            http.secure(sslCertificate, sslCertificatePassword, null, null)
        }
        http.before { req, res ->
            res.header(HttpHelper.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
            res.header(HttpHelper.ACCESS_CONTROL_REQUEST_METHOD, "POST, GET, OPTIONS")
            //res.header("Access-Control-Allow-Headers", "")
            res.type("application/json")

            // This is to provide compatibility with old postchain-client code
            req.pathInfo()
                    .takeIf { it.endsWith("/") }
                    ?.also { res.redirect(it.dropLast(1)) }
        }

    }

    private fun error(error: Exception): String {
        return gson.toJson(ErrorBody(error.message ?: "Unknown error"))
    }
}