package net.postchain.api.rest.controller

import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.StatusPages
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.request.uri
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.server.engine.*
import io.ktor.server.netty.Netty
import mu.KLogging
import net.postchain.api.rest.json.JsonFactory
import net.postchain.core.UserMistake
import java.io.FileInputStream
import java.security.KeyStore
import java.util.concurrent.TimeUnit


open class HttpServer(private val listenPort: Int,
                      private val sslCertificate: String? = null,
                      private val sslCertificatePassword: String? = null,
                      private val restApiSslCertificateAlias: String? = null) {

    lateinit var http: ApplicationEngine
    private var initilized = false
    companion object : KLogging()

    init {
        initialize(listenPort, sslCertificate, sslCertificatePassword, restApiSslCertificateAlias)
    }

    private val gson = JsonFactory.makeJson()

    fun initialize(listenPort: Int, sslCertificate: String? = null, sslCertificatePassword: String? = null, restApiSslCertificateAlias: String? = null) {
        if (!initilized) {
            logger.info ("HttpServer is being initialized....")

            val env = applicationEngineEnvironment {
                module {
                    install(StatusPages) {
                        exception<NotFoundError> { cause ->
                            logger.error("NotFoundError:", cause.message)
                            call.respondText(cause.message ?: "",
                                    ContentType.Text.Plain, HttpStatusCode.NotFound){}
                        }
                        exception<BadFormatError> { cause ->
                            logger.error("BadFormatError:", cause.message)
                            call.respondText(cause.message ?: "",
                                    ContentType.Text.Plain, HttpStatusCode.BadRequest){}
                        }
                        exception<UserMistake> { cause ->
                            logger.error("UserMistake:", cause.message)
                            call.respondText(cause.message ?: "",
                                    ContentType.Text.Plain, HttpStatusCode.BadRequest){}
                        }
                        exception<OverloadedException> { cause ->
                            logger.error("OverloadedException:", cause.message)
                            call.respondText(cause.message ?: "",
                                    ContentType.Text.Plain, HttpStatusCode.ServiceUnavailable){}
                        }
                        exception<Exception> { cause ->
                            logger.error("Exception:", cause.message)
                            call.respondText(cause.message ?: "",
                                    ContentType.Text.Plain, HttpStatusCode.InternalServerError){}
                        }
                    }
                    intercept(ApplicationCallPipeline.Call) {
                        call.response.headers.append(HttpHelper.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                        call.response.headers.append(HttpHelper.ACCESS_CONTROL_REQUEST_METHOD, "POST, GET, OPTIONS")
                        // call.response.headers.append(HttpHelper.CONTENT_TYPE, "application/json")
                        if(call.request.uri.endsWith("/")) {
                            call.respondRedirect(call.request.uri.dropLast(1))
                        }
                    }
                }

                if (sslCertificate != null && sslCertificate.isNotBlank()) {
                    sslConnector(
                            keyStore = loadKeystore(sslCertificate!!, sslCertificatePassword!!),
                            keyAlias = restApiSslCertificateAlias!!,
                            keyStorePassword = ({ sslCertificatePassword!!.toCharArray() }),
                            privateKeyPassword = ({ sslCertificatePassword!!.toCharArray() })
                    ) {
                        host = "0.0.0.0"
                        port = listenPort
                    }
                } else {
                    connector {
                        host = "0.0.0.0"
                        port = listenPort
                    }
                }
            }
            http = embeddedServer( Netty,  env) {
                connectionGroupSize = 1
                workerGroupSize = 5
            }
            http.start()
            initilized = true
        }

    }
    private fun loadKeystore(keyStoreFileLocation: String, keyStorePassword: String): KeyStore {
        //io.ktor.network.tls.certificates.generateCertificate(File(keyStoreFileLocation))
        val keyStore = KeyStore.getInstance("JKS")
        keyStore.load(FileInputStream(keyStoreFileLocation), keyStorePassword.toCharArray())
        return keyStore
    }

    fun stop() {
        try {
            http.stop(5, 5, TimeUnit.SECONDS)
               // Ugly hack to workaround that there is no blocking stop.
            // Test cases won't work correctly without it
            Thread.sleep(5000)
        } finally {
            initilized = false
        }

    }

    private fun error(error: Exception): String {
        return gson.toJson(ErrorBody(error.message ?: "Unknown error"))
    }
}