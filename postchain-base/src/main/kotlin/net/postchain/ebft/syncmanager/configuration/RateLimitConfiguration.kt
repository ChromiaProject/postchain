package net.postchain.ebft.syncmanager.configuration

import net.postchain.config.app.AppConfig

class RateLimitConfiguration(
        val blockRequestRateLimit: Long,
) {

    companion object {

        @JvmStatic
        fun fromAppConfig(config: AppConfig) = RateLimitConfiguration(
                config.getEnvOrLong("POSTCHAIN_RATE_LIMIT_BLOCKS", "rate-limit.blocks", 100)
        )
    }
}