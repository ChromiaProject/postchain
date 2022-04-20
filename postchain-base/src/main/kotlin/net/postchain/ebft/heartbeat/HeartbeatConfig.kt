package net.postchain.ebft.heartbeat

import net.postchain.config.app.AppConfig

data class HeartbeatConfig(
        val enabled: Boolean,
        val testmode: Boolean,
        val timeout: Long,
        val sleepTimeout: Long,
        val remoteConfigEnabled: Boolean,
        val remoteConfigRequestInterval: Long,
        val remoteConfigTimeout: Long
) {
    companion object {
        @JvmStatic
        fun fromAppConfig(config: AppConfig): HeartbeatConfig {
            return HeartbeatConfig(
                    config.getBoolean("heartbeat.enabled", false),
                    config.getBoolean("heartbeat.testmode", false),
                    config.getLong("heartbeat.timeout", 60_000L),
                    config.getLong("heartbeat.sleep_timeout", 5_000L),
                    config.getBoolean("remote_config.enabled", true),
                    config.getLong("remote_config.request_interval", 20_000L),
                    config.getLong("remote_config.request_timeout", 60_000L)
            )
        }
    }
}