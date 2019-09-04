package net.postchain.api.rest.controller

import spark.Service

class HttpServer {

    val http = Service.ignite()!!

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
}