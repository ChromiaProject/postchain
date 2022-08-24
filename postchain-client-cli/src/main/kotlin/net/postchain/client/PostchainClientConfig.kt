package net.postchain.client

import net.postchain.common.PropertiesFileLoader
import net.postchain.common.hexStringToByteArray
import net.postchain.common.BlockchainRid
import net.postchain.client.core.ConcretePostchainClient

data class PostchainClientConfig(
        val apiUrl: String,
        val blockchainRid: BlockchainRid,
        val retrieveTxStatusAttempts: Int?,
        private val privKey: String,
        private val pubKey: String
) {
    val pubKeyByteArray = pubKey.hexStringToByteArray()
    val privKeyByteArray = privKey.hexStringToByteArray()

    companion object {
        @JvmStatic
        fun fromProperties(propertiesFileName: String): PostchainClientConfig {
            val config = PropertiesFileLoader.load(propertiesFileName)

            return PostchainClientConfig(
                    apiUrl = System.getenv("POSTCHAIN_CLIENT_API_URL")
                            ?: config.getString("api-url", ""),
                    blockchainRid = (System.getenv("POSTCHAIN_CLIENT_BLOCKCHAIN_RID")
                            ?: config.getString("blockchain-rid", "")).let { BlockchainRid.buildFromHex(it) },
                    retrieveTxStatusAttempts = (System.getenv("RETRIEVE_TX_STATUS_ATTEMPTS")
                            ?: config.getString("retrieveTxStatusAttempts", ""))
                            .let { if (null == it || it.isEmpty()) null else it.toInt() },
                    privKey = System.getenv("POSTCHAIN_CLIENT_PRIVKEY")
                            ?: config.getString("privkey", ""),
                    pubKey = System.getenv("POSTCHAIN_CLIENT_PUBKEY")
                            ?: config.getString("pubkey", "")
            )
        }
    }
}
