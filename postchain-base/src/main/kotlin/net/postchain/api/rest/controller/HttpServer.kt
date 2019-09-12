package net.postchain.api.rest.controller

import mu.KLogging
import net.postchain.api.rest.json.JsonFactory
import net.postchain.core.UserMistake
import spark.Service

open class HttpServer {

    val http = Service.ignite()!!
    companion object {
        private var initilized = false
        val logger = KLogging().logger
    }

    private val stoppedNodePaths = mutableSetOf<String>()

    private val gson = JsonFactory.makeJson()

    fun buildErrorHandler() {
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
        if (!initilized) {
            logger.info ("HttpServer is being initialized....")
            http.port(listenPort)
            if (sslCertificate != null) {
                http.secure(sslCertificate, sslCertificatePassword, null, null)
            }
            http.before { req, res ->
                res.header(HttpHelper.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                res.header(HttpHelper.ACCESS_CONTROL_REQUEST_METHOD, "POST, GET, OPTIONS")
                //res.header("Access-Control-Allow-Headers", "")
                res.type("application/json")

                for (path in stoppedNodePaths) {
                    if (req.pathInfo().startsWith(path)) {
                        throw NotFoundError("Not found path: $path")
                    }
                }

                // This is to provide compatibility with old postchain-client code
                req.pathInfo()
                        .takeIf { it.endsWith("/") }
                        ?.also { res.redirect(it.dropLast(1)) }
            }
            initilized = true
        }

    }

    fun stop() {
        try {
            http.stop()
            // Ugly hack to workaround that there is no blocking stop.
            // Test cases won't work correctly without it
            Thread.sleep(100)
        } finally {
            initilized = false
            stoppedNodePaths.clear()
        }

    }

    @Synchronized
    fun addStoppedNodePaths(path : String) {
        stoppedNodePaths.add(path)
    }

    @Synchronized
    fun removeStoppedNodePaths(path : String) {
        stoppedNodePaths.remove(path)
    }

    private fun error(error: Exception): String {
        return gson.toJson(ErrorBody(error.message ?: "Unknown error"))
    }
}