package net.postchain.client.config

import net.postchain.common.config.getEnvOrIntProperty
import net.postchain.common.config.getEnvOrLongProperty
import org.apache.commons.configuration2.Configuration
import java.time.Duration

val FAIL_OVER_INTERVAL: Duration = Duration.ofMillis(500)

data class FailOverConfig(
    val attemptsPerEndpoint: Int = 5,
    val attemptInterval: Duration = FAIL_OVER_INTERVAL,
) {
    companion object {
        fun fromConfiguration(config: Configuration): FailOverConfig {
            return FailOverConfig(
                attemptsPerEndpoint = config.getEnvOrIntProperty("POSTCHAIN_CLIENT_FAIL_OVER_ATTEMPTS", "failover.attempts", 5),
                attemptInterval = config.getEnvOrLongProperty("POSTCHAIN_CLIENT_FAIL_OVER_INTERVAL", "failover.interval", FAIL_OVER_INTERVAL.toMillis()).let { Duration.ofMillis(it) }
            )
        }
    }
}
