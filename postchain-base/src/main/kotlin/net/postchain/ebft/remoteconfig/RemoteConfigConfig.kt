package net.postchain.ebft.remoteconfig

import net.postchain.common.config.Config
import net.postchain.config.app.AppConfig

/**
 * RemoteConfig
 *
 * @param enabled Enables/disables remote config check (for subnode only)
 * @param sleepTimeout BlockchainProcess sleeps for `sleepTimeout` ms after every failed check
 * @param requestInterval Remote config is requested every `max(maxBlockTime, remoteConfigRequestInterval)` ms
 * @param requestTimeout Remote config check is failed if there is no remote config response registered for the last `max(maxBlockTime, remoteConfigTimeout)` ms
 */
data class RemoteConfigConfig(
        val enabled: Boolean,
        val sleepTimeout: Long,
        val requestInterval: Long,
        val requestTimeout: Long
) : Config {
    companion object {
        @JvmStatic
        fun fromAppConfig(config: AppConfig): RemoteConfigConfig {
            return RemoteConfigConfig(
                    config.getBoolean("remote_config.enabled", true),
                    config.getLong("remote_config.sleep_timeout", 5_000L),
                    config.getLong("remote_config.request_interval", 20_000L),
                    config.getLong("remote_config.request_timeout", 60_000L)
            )
        }
    }
}