package net.postchain.api.rest.infra

import net.postchain.config.app.AppConfig

data class RestApiConfig(
        val basePath: String,
        val port: Int,
        val ssl: Boolean,
        val sslCertificate: String,
        val sslCertificatePassword: String
) {
    companion object {
        @JvmStatic
        fun fromAppConfig(config: AppConfig): RestApiConfig {
            return RestApiConfig(
                    config.getString("api.basepath", ""),
                    config.getInt("api.port", 7740),
                    config.getBoolean("api.enable_ssl", false),
                    config.getString("api.ssl_certificate", ""),
                    config.getString("api.ssl_certificate.password", "")
            )
        }
    }
}
