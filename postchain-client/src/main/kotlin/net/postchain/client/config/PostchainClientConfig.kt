package net.postchain.client.config

import net.postchain.common.BlockchainRid
import net.postchain.common.PropertiesFileLoader
import net.postchain.crypto.KeyPair

const val STATUS_POLL_COUNT = 20
const val STATUS_POLL_INTERVAL = 500L //ms
data class PostchainClientConfig(
    val apiUrl: String,
    val blockchainRid: BlockchainRid,
    val keyPair: KeyPair,
    val statusPollCount: Int = STATUS_POLL_COUNT,
    val statusPollInterval: Long = STATUS_POLL_INTERVAL,
) {
    val pubKeyByteArray = keyPair.pubKey.key
    val privKeyByteArray = keyPair.privKey.key

    companion object {
        @JvmStatic
        fun fromProperties(propertiesFileName: String): PostchainClientConfig {
            val config = PropertiesFileLoader.load(propertiesFileName)

            return PostchainClientConfig(
                apiUrl = System.getenv("POSTCHAIN_CLIENT_API_URL")
                    ?: config.getString("api.url", ""),
                blockchainRid = (System.getenv("POSTCHAIN_CLIENT_BLOCKCHAIN_RID")
                    ?: config.getString("brid", "")).let { BlockchainRid.buildFromHex(it) },
                keyPair = KeyPair.of(
                    System.getenv("POSTCHAIN_CLIENT_PUBKEY")
                        ?: config.getString("pubkey", ""),
                    System.getenv("POSTCHAIN_CLIENT_PRIVKEY")
                        ?: config.getString("privkey", ""),
                    ),
                statusPollCount = System.getenv("POSTCHAIN_CLIENT_STATUS_POLL_COUNT")?.toInt()
                    ?: config.getInt("status.poll-count", STATUS_POLL_COUNT),
                statusPollInterval = System.getenv("POSTCHAIN_CLIENT_STATUS_POLL_INTERVAL")?.toLong()
                    ?: config.getLong("status.poll-interval", STATUS_POLL_INTERVAL)
            )
        }
    }
}
