package net.postchain.containers.bpm.bcconfig

import net.postchain.common.config.Config
import net.postchain.config.app.AppConfig

/**
 * SubnodeBlockchainConfigurationConfig
 *
 * @param enabled Enables/disables remote config check (for subnode only)
 * @param sleepTimeout BlockchainProcess sleeps for `sleepTimeout` ms after every failed check
 */
data class SubnodeBlockchainConfigurationConfig(
        val enabled: Boolean,
        val sleepTimeout: Long
) : Config {
    companion object {
        @JvmStatic
        fun fromAppConfig(config: AppConfig): SubnodeBlockchainConfigurationConfig {
            return SubnodeBlockchainConfigurationConfig(
                    config.getEnvOrBoolean("POSTCHAIN_SUBNODE_BLOCKCHAIN_CONFIG_ENABLED", "subnode_blockchain_config.enabled", true),
                    config.getEnvOrLong("POSTCHAIN_SUBNODE_BLOCKCHAIN_CONFIG_SLEEP_TIMEOUT", "subnode_blockchain_config.sleep_timeout", 5_000L)
            )
        }
    }
}