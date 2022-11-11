package net.postchain.containers.bpm.bcconfig

import net.postchain.common.config.Config
import net.postchain.config.app.AppConfig

/**
 * SubnodeBlockchainConfigurationConfig
 *
 * @param enabled Enables/disables remote config check (for subnode only)
 * @param sleepTimeout BlockchainProcess sleeps for `sleepTimeout` ms after every failed check
 * @param requestInterval Remote config is requested every `max(maxBlockTime, requestInterval)` ms
 * @param requestTimeout Remote config check is failed if there is no remote config response registered for the last `max(maxBlockTime, requestTimeout)` ms
 */
data class SubnodeBlockchainConfigurationConfig(
        val enabled: Boolean,
        val sleepTimeout: Long,
        val requestInterval: Long,
        val requestTimeout: Long
) : Config {
    companion object {
        @JvmStatic
        fun fromAppConfig(config: AppConfig): SubnodeBlockchainConfigurationConfig {
            return SubnodeBlockchainConfigurationConfig(
                    config.getBoolean("subnode_blockchain_config.enabled", true),
                    config.getLong("subnode_blockchain_config.sleep_timeout", 5_000L),
                    config.getLong("subnode_blockchain_config.request_interval", 20_000L),
                    config.getLong("subnode_blockchain_config.request_timeout", 60_000L)
            )
        }
    }
}