package net.postchain.api.rest.infra

import net.postchain.common.config.Config
import net.postchain.config.app.AppConfig

data class RestApiConfig(
        val basePath: String,
        val port: Int,
        val debugPort: Int,
        val gracefulShutdown: Boolean = true,
        val requestConcurrency: Int = 0,
        val requestConcurrencyLocal: Int = -1,
        val requestConcurrencyExternal: Int = -1,
        val chainRequestConcurrency: Int = -1,
        val containerRequestConcurrency: Int = 0,
        val subnodeHttpRedirect: Boolean = false,
        val maxRequestBodySize: Int,
        val maxDataSize: Int
) : Config {

    init {
        require(port in -1 .. 49151) { "API port has to be between -1 (disabled) and 49151 (ephemeral)" }
        require(debugPort in -1 .. 49151) { "Debug port has to be between -1 (disabled) and 49151 (ephemeral)" }
        require(requestConcurrency >= 0) { "Request concurrency cannot be negative" }
        require(requestConcurrencyLocal >= -1) { "Request concurrency local has to be positive, 0 (dynamic set) or -1 (no limit)" }
        require(requestConcurrencyExternal >= -1) { "Request concurrency external has to be positive, 0 (dynamic set) or -1 (no limit)" }
        require(chainRequestConcurrency > 0 || chainRequestConcurrency == -1) { "Chain request concurrency has to be positive or -1 (no limit)" }
        require(containerRequestConcurrency >= -1) { "Container request concurrency has to be positive, 0 (dynamic set) or -1 (no limit)" }
        require(maxRequestBodySize in 0 .. Int.MAX_VALUE) { "Max request body size has to be between 0 (disabled) and 2147483647" }
        require(maxDataSize in 0 .. Int.MAX_VALUE) { "Max data size for a database query result, has to be between 0 and 2147483647" }
    }

    companion object {

        const val DEFAULT_REST_API_PORT = 7740
        const val DEFAULT_DEBUG_API_PORT = 7750
        const val DEFAULT_MAX_REQUEST_BODY_SIZE = 1024 * 1024 * 55 // 52mb = default-tx-max-size-26mb * 2 + margin
        const val DEFAULT_MAX_DATA_SIZE = 1024 * 1024 * 55

        @JvmStatic
        fun fromAppConfig(config: AppConfig): RestApiConfig {
            return RestApiConfig(
                    config.getEnvOrString("POSTCHAIN_API_BASEPATH", "api.basepath", ""),
                    config.getEnvOrInt("POSTCHAIN_API_PORT", "api.port", DEFAULT_REST_API_PORT),
                    config.getEnvOrInt("POSTCHAIN_DEBUG_PORT", "debug.port", DEFAULT_DEBUG_API_PORT),
                    config.getBoolean("api.graceful-shutdown", true),
                    config.getEnvOrInt("POSTCHAIN_API_REQUEST_CONCURRENCY", "api.request-concurrency", 0),
                    config.getEnvOrInt("POSTCHAIN_API_REQUEST_CONCURRENCY_LOCAL", "api.request-concurrency.local", 0),
                    config.getEnvOrInt("POSTCHAIN_API_REQUEST_CONCURRENCY_EXTERNAL", "api.request-concurrency.external", 0),
                    config.getEnvOrInt("POSTCHAIN_API_CHAIN_REQUEST_CONCURRENCY", "api.chain-request-concurrency", -1),
                    config.getEnvOrInt("POSTCHAIN_API_CONTAINER_REQUEST_CONCURRENCY", "api.container-request-concurrency", 0),
                    config.getEnvOrBoolean("POSTCHAIN_API_SUBNODE_HTTP_REDIRECT", "api.subnode-http-redirect", false),
                    config.getEnvOrInt("POSTCHAIN_API_MAX_REQUEST_BODY_SIZE", "api.max-request-body-size", DEFAULT_MAX_REQUEST_BODY_SIZE),
                    config.getEnvOrInt("POSTCHAIN_API_MAX_DATA_SIZE", "api.max-data-size", DEFAULT_MAX_DATA_SIZE),
            )
        }
    }
}
