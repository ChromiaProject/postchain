package net.postchain.client.config

import net.postchain.client.impl.AbortOnErrorRequestStrategyFactory
import net.postchain.client.request.EndpointPool
import net.postchain.client.request.RequestStrategyFactory
import net.postchain.common.BlockchainRid
import net.postchain.common.PropertiesFileLoader
import net.postchain.common.config.Config
import net.postchain.common.config.cryptoSystem
import net.postchain.common.config.getEnvOrIntProperty
import net.postchain.common.config.getEnvOrListProperty
import net.postchain.common.config.getEnvOrLongProperty
import net.postchain.common.config.getEnvOrStringProperty
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.KeyPair
import net.postchain.crypto.Secp256K1CryptoSystem
import org.apache.commons.configuration2.BaseConfiguration
import org.apache.commons.configuration2.Configuration
import java.time.Duration

const val STATUS_POLL_COUNT = 20
val STATUS_POLL_INTERVAL: Duration = Duration.ofMillis(500)
const val MAX_RESPONSE_SIZE = 64 * 1024 * 1024 // 64 MiB
val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(60)
val RESPONSE_TIMEOUT: Duration = Duration.ofSeconds(60)

data class PostchainClientConfig @JvmOverloads constructor(
        val blockchainRid: BlockchainRid,
        val endpointPool: EndpointPool,
        val signers: List<KeyPair> = listOf(),
        val statusPollCount: Int = STATUS_POLL_COUNT,
        val statusPollInterval: Duration = STATUS_POLL_INTERVAL,
        val failOverConfig: FailOverConfig = FailOverConfig(),
        val cryptoSystem: CryptoSystem = Secp256K1CryptoSystem(),
        val queryByChainId: Long? = null,
        val maxResponseSize: Int = MAX_RESPONSE_SIZE,
        /** Will not be used if `httpClient` parameter is specified when creating `ConcretePostchainClient`. */
        val connectTimeout: Duration = CONNECT_TIMEOUT,
        /** Will not be used if `httpClient` parameter is specified when creating `ConcretePostchainClient`. */
        val responseTimeout: Duration = RESPONSE_TIMEOUT,
        val requestStrategy: RequestStrategyFactory = AbortOnErrorRequestStrategyFactory()
) : Config {
    companion object {
        @JvmStatic
        fun fromProperties(propertiesFileName: String?): PostchainClientConfig =
                fromConfiguration(propertiesFileName?.let { PropertiesFileLoader.load(it) } ?: BaseConfiguration())

        fun fromConfiguration(config: Configuration): PostchainClientConfig {
            val pubkeys = config.getEnvOrListProperty("POSTCHAIN_CLIENT_PUBKEY", "pubkey", listOf())
            val privkeys = config.getEnvOrListProperty("POSTCHAIN_CLIENT_PRIVKEY", "privkey", listOf())
            require(pubkeys.size == privkeys.size) { "Equally many pubkeys as privkeys must be provided, but ${pubkeys.size} and ${privkeys.size} was found" }
            val signers = if (privkeys.isEmpty() || privkeys.first() == "") listOf() else pubkeys.zip(privkeys).map { KeyPair.of(it.first, it.second) }
            return PostchainClientConfig(
                    blockchainRid = config.getEnvOrStringProperty("POSTCHAIN_CLIENT_BLOCKCHAIN_RID", "brid", "").let { BlockchainRid.buildFromHex(it) },
                    endpointPool = EndpointPool.default(config.getEnvOrStringProperty("POSTCHAIN_CLIENT_API_URL", "api.url", "").split(",")),
                    signers = signers,
                    statusPollCount = config.getEnvOrIntProperty("POSTCHAIN_CLIENT_STATUS_POLL_COUNT", "status.poll-count", STATUS_POLL_COUNT),
                    statusPollInterval = config.getEnvOrLongProperty("POSTCHAIN_CLIENT_STATUS_POLL_INTERVAL", "status.poll-interval", STATUS_POLL_INTERVAL.toMillis()).let { Duration.ofMillis(it) },
                    failOverConfig = FailOverConfig.fromConfiguration(config),
                    cryptoSystem = config.cryptoSystem(),
                    maxResponseSize = config.getEnvOrIntProperty("POSTCHAIN_CLIENT_MAX_RESPONSE_SIZE", "max.response.size", MAX_RESPONSE_SIZE),
                    connectTimeout = config.getEnvOrLongProperty("POSTCHAIN_CLIENT_CONNECT_TIMEOUT", "connect.timeout", CONNECT_TIMEOUT.toMillis()).let { Duration.ofMillis(it) },
                    responseTimeout = config.getEnvOrLongProperty("POSTCHAIN_CLIENT_RESPONSE_TIMEOUT", "response.timeout", RESPONSE_TIMEOUT.toMillis()).let { Duration.ofMillis(it) },
                    requestStrategy = config.getEnvOrStringProperty("POSTCHAIN_CLIENT_REQUEST_STRATEGY", "request.strategy", RequestStrategies.ABORT_ON_ERROR.name)
                            .let { RequestStrategies.valueOf(it).factory },
            )
        }
    }
}
