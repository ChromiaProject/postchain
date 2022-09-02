package net.postchain.client.config

import net.postchain.common.BlockchainRid
import net.postchain.common.PropertiesFileLoader
import net.postchain.common.config.cryptoSystem
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.KeyPair
import net.postchain.crypto.Secp256K1CryptoSystem

const val STATUS_POLL_COUNT = 20
const val STATUS_POLL_INTERVAL = 500L //ms
data class PostchainClientConfig(
    val apiUrl: String,
    val blockchainRid: BlockchainRid,
    val signers: List<KeyPair> = listOf(),
    val statusPollCount: Int = STATUS_POLL_COUNT,
    val statusPollInterval: Long = STATUS_POLL_INTERVAL,
    val cryptoSystem: CryptoSystem = Secp256K1CryptoSystem()
) {

    companion object {
        @JvmStatic
        fun fromProperties(propertiesFileName: String): PostchainClientConfig {
            val config = PropertiesFileLoader.load(propertiesFileName)
            val pubkeys = (System.getenv("POSTCHAIN_CLIENT_PUBKEY")
                ?: config.getString("pubkey", "")).split(",")
            val privkeys = (System.getenv("POSTCHAIN_CLIENT_PRIVKEY")
                ?: config.getString("privkey", "")).split(",")
            require(pubkeys.size == privkeys.size) { "Equally manny pubkeys as privkeys must be provider, but ${pubkeys.size} and ${privkeys.size} was found"}
            val signers = pubkeys.zip(privkeys).map { KeyPair.of(it.first, it.second) }
            return PostchainClientConfig(
                apiUrl = System.getenv("POSTCHAIN_CLIENT_API_URL")
                    ?: config.getString("api.url", ""),
                blockchainRid = (System.getenv("POSTCHAIN_CLIENT_BLOCKCHAIN_RID")
                    ?: config.getString("brid", "")).let { BlockchainRid.buildFromHex(it) },
                signers = signers,
                statusPollCount = System.getenv("POSTCHAIN_CLIENT_STATUS_POLL_COUNT")?.toInt()
                    ?: config.getInt("status.poll-count", STATUS_POLL_COUNT),
                statusPollInterval = System.getenv("POSTCHAIN_CLIENT_STATUS_POLL_INTERVAL")?.toLong()
                    ?: config.getLong("status.poll-interval", STATUS_POLL_INTERVAL),
                cryptoSystem = config.cryptoSystem()
            )
        }
    }
}
