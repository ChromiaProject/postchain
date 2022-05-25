package net.postchain.eif.config

import net.postchain.config.app.AppConfig

data class EifConfig(
        // Can be HTTP address or socket file path for IPC
        val url: String,
        val connectTimeout: Long,
        val readTimeout: Long,
        val writeTimeout: Long
) {
    companion object {
        @JvmStatic
        fun fromAppConfig(config: AppConfig): EifConfig {
            return EifConfig(
                    config.getString("ethereum.url", ""),
                    config.getLong("ethereum.connectTimeout", 300),
                    config.getLong("ethereum.readTimeout", 300),
                    config.getLong("ethereum.writeTimeout", 300)
            )
        }
    }
}
