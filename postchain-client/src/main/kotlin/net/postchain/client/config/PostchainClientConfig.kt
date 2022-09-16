package net.postchain.client.config

import net.postchain.client.request.EndpointPool
import net.postchain.common.BlockchainRid
import net.postchain.common.PropertiesFileLoader
import net.postchain.common.config.cryptoSystem
import net.postchain.common.config.getEnvOrIntProperty
import net.postchain.common.config.getEnvOrLongProperty
import net.postchain.common.config.getEnvOrStringProperty
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.KeyPair
import net.postchain.crypto.Secp256K1CryptoSystem

const val STATUS_POLL_COUNT = 20
const val STATUS_POLL_INTERVAL = 500L //ms
data class PostchainClientConfig(
    val blockchainRid: BlockchainRid,
    val endpointPool: EndpointPool,
    val signers: List<KeyPair> = listOf(),
    val statusPollCount: Int = STATUS_POLL_COUNT,
    val statusPollInterval: Long = STATUS_POLL_INTERVAL,
    // Fail-over only applicable to synchronized requests
    val failOverConfig: FailOverConfig = FailOverConfig(),
    val cryptoSystem: CryptoSystem = Secp256K1CryptoSystem()
) {

    companion object {
        @JvmStatic
        fun fromProperties(propertiesFileName: String): PostchainClientConfig {
            val config = PropertiesFileLoader.load(propertiesFileName)
            val pubkeys = config.getEnvOrStringProperty("POSTCHAIN_CLIENT_PUBKEY", "pubkey", "").split(",")
            val privkeys = config.getEnvOrStringProperty("POSTCHAIN_CLIENT_PRIVKEY", "privkey", "").split(",")
            require(pubkeys.size == privkeys.size) { "Equally many pubkeys as privkeys must be provided, but ${pubkeys.size} and ${privkeys.size} was found"}
            val signers = pubkeys.zip(privkeys).map { KeyPair.of(it.first, it.second) }
            return PostchainClientConfig(
                blockchainRid = config.getEnvOrStringProperty("POSTCHAIN_CLIENT_BLOCKCHAIN_RID", "brid", "").let { BlockchainRid.buildFromHex(it) },
                endpointPool = EndpointPool.default(config.getEnvOrStringProperty("POSTCHAIN_CLIENT_API_URL", "api.url", "").split(",")),
                signers = signers,
                statusPollCount = config.getEnvOrIntProperty("POSTCHAIN_CLIENT_STATUS_POLL_COUNT", "status.poll-count", STATUS_POLL_COUNT),
                statusPollInterval = config.getEnvOrLongProperty("POSTCHAIN_CLIENT_STATUS_POLL_INTERVAL", "status.poll-interval", STATUS_POLL_INTERVAL),
                cryptoSystem = config.cryptoSystem()
            )
        }
    }
}
