package net.postchain.api.rest.infra

import net.postchain.config.app.AppConfig
import net.postchain.config.app.Config

data class RestApiConfig(
        val basePath: String,
        val port: Int,
        val tls: Boolean,
        val tlsCertificate: String,
        val tlsCertificatePassword: String,
        val debug: Boolean = false
) : Config {

    companion object {

        const val DEFAULT_REST_API_PORT = 7740

        @JvmStatic
        fun fromAppConfig(config: AppConfig): RestApiConfig {
            return RestApiConfig(
                    config.getString("api.basepath", ""),
                    config.getInt("api.port", DEFAULT_REST_API_PORT),
                    config.getBoolean("api.enable_tls", config.getBoolean("api.enable_ssl", false)),
                    config.getString("api.tls_certificate", config.getString("api.ssl_certificate", "")),
                    config.getString("api.tls_certificate.password", config.getString("api.ssl_certificate.password", "")),
                    config.debug
            )
        }
    }
}
