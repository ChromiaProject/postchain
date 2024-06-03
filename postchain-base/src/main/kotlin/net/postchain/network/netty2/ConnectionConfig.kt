package net.postchain.network.netty2

import net.postchain.common.config.Config
import net.postchain.config.app.AppConfig

/**
 * This data class holds the configuration parameters required for handling connections.
 * It uses these parameters to specify timeout values and ping intervals.
 *
 * @property readHandshakeTimeout A value (in milliseconds) specifying the maximum time to wait for a handshake message.
 * If the handshake message is not received within this time, the connection is considered failed and will be closed.
 *
 * @constructor Creates a new ConnectionConfig instance.
 */
data class ConnectionConfig(
        val readHandshakeTimeout: Long = 10_000,
) : Config {

    companion object {
        @JvmStatic
        fun fromAppConfig(config: AppConfig): ConnectionConfig {
            return ConnectionConfig(
                    readHandshakeTimeout = config.getEnvOrLong("POSTCHAIN_CONNECTION_READ_HANDSHAKE_TIMEOUT", "connection.read_handshake_timeout", 10_000),
            )
        }
    }
}
