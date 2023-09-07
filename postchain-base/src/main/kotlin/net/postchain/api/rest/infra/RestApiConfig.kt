package net.postchain.api.rest.infra

import net.postchain.common.config.Config
import net.postchain.config.app.AppConfig

data class RestApiConfig(
        val basePath: String,
        val port: Int,
        val debugPort: Int,
        val debug: Boolean = false,
        val gracefulShutdown: Boolean = true,
        val requestConcurrency: Int = 0
) : Config {

    init {
        require(port in -1 .. 49151) { "API port has to be between -1 (disabled) and 49151 (ephemeral)" }
        require(debugPort in -1 .. 49151) { "Debug port has to be between -1 (disabled) and 49151 (ephemeral)" }
    }

    companion object {

        const val DEFAULT_REST_API_PORT = 7740
        const val DEFAULT_DEBUG_API_PORT = 7750

        @JvmStatic
        fun fromAppConfig(config: AppConfig): RestApiConfig {
            return RestApiConfig(
                    config.getEnvOrString("POSTCHAIN_API_BASEPATH", "api.basepath", ""),
                    config.getEnvOrInt("POSTCHAIN_API_PORT", "api.port", DEFAULT_REST_API_PORT),
                    config.getEnvOrInt("POSTCHAIN_DEBUG_PORT", "debug.port", DEFAULT_DEBUG_API_PORT),
                    config.debug,
                    config.getBoolean("api.graceful-shutdown", true),
                    config.getEnvOrInt("POSTCHAIN_API_REQUEST_CONCURRENCY", "api.request-concurrency", config.databaseReadConcurrency)
            )
        }
    }
}
