package net.postchain.client

import net.postchain.common.PropertiesFileLoader
import net.postchain.core.BlockchainRid
import net.postchain.crypto.KeyPair

data class PostchainClientConfig(
        val apiUrl: String,
        val blockchainRid: BlockchainRid,
        val keyPair: KeyPair
) {
    val pubKey = keyPair.pubKey
    val privKey = keyPair.privKey

    companion object {
        @JvmStatic
        fun fromProperties(propertiesFileName: String): PostchainClientConfig {
            val config = PropertiesFileLoader.load(propertiesFileName)
            return PostchainClientConfig(
                    apiUrl = System.getenv("POSTCHAIN_CLIENT_API_URL")
                            ?: config.getString("api-url", ""),
                    blockchainRid = (System.getenv("POSTCHAIN_CLIENT_BLOCKCHAIN_RID")
                            ?: config.getString("blockchain-rid", "")).let { BlockchainRid.buildFromHex(it) },
                    keyPair = KeyPair.fromStrings(
                            System.getenv("POSTCHAIN_CLIENT_PUBKEY")
                                    ?: config.getString("pubkey", ""),
                            System.getenv("POSTCHAIN_CLIENT_PRIVKEY")
                                    ?: config.getString("privkey", "")
                    )
            )
        }
    }
}
