package net.postchain.client.config

import net.postchain.common.config.getEnvOrIntProperty
import net.postchain.common.config.getEnvOrLongProperty
import org.apache.commons.configuration2.Configuration

const val FAIL_OVER_INTERVAL = 500L //ms

data class FailOverConfig(
    val attemptsPerEndpoint: Int = 5,
    val attemptInterval: Long = FAIL_OVER_INTERVAL,
) {
    companion object {
        fun fromConfiguration(config: Configuration): FailOverConfig {
            return FailOverConfig(
                attemptsPerEndpoint = config.getEnvOrIntProperty("POSTCHAIN_CLIENT_FAIL_OVER_ATTEMPTS", "failover.attempts", 5),
                attemptInterval = config.getEnvOrLongProperty("POSTCHAIN_CLIENT_FAIL_OVER_INTERVAL", "failover.interval", FAIL_OVER_INTERVAL)
            )
        }
    }
}
